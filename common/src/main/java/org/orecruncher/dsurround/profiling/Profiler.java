package org.orecruncher.dsurround.profiling;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.effects.RenderTimers;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.IMinecraftDirectories;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * /dsprofile: sets up a scene ({@link ProfileScene}), records the game with Java Flight Recorder while it shows, and
 * puts it back. The recording goes in the mod's profiles folder, zipped (see {@link ProfileFiles}), with the mod's
 * render timers in it and nothing private (see {@link ProfileRecording}); when it is done, the player is told where it
 * is, with the render timers, and the log says so too. While it waits and records, {@link #state} and {@link #secondsLeft} drive the
 * indicator on screen.
 * <p>
 * Java Flight Recorder needs the {@code jdk.jfr} module, which not every Java has: without it, the player is told, and
 * nothing that uses it is loaded.
 */
public final class Profiler {

    private static final String KEY = "dsurround.command.dsprofile.";

    /**
     * Where a profile is
     */
    public enum State {
        /**
         * None is being made
         */
        IDLE,
        /**
         * Set up, waiting for the scene to settle before recording
         */
        WAITING,
        /**
         * Recording
         */
        RECORDING
    }

    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static volatile State state = State.IDLE;
    // When what it is doing now ends, by System.nanoTime
    private static volatile long until;

    private Profiler() {
    }

    public static State state() {
        return state;
    }

    /**
     * Seconds until it starts recording, while waiting, or until it is done, while recording
     */
    public static long secondsLeft() {
        return Math.max(0L, (until - System.nanoTime() + 999_999_999L) / 1_000_000_000L);
    }

    /**
     * Whether Java Flight Recorder can record here: this Java has the {@code jdk.jfr} module, and it hasn't been turned
     * off. Checked for the module first, so that without it nothing using it is loaded.
     */
    public static boolean isAvailable() {
        if (ModuleLayer.boot().findModule("jdk.jfr").isEmpty())
            return false;
        try {
            return ProfileRecording.isAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Sets the scene up and starts recording, on the render thread. The recording runs on a thread of its own.
     *
     * @return what to tell the player
     */
    public static List<Component> start(ProfileScene scene, int seconds) {
        if (!isAvailable())
            return List.of(Component.translatable(KEY + "unavailable"));
        if (!BUSY.compareAndSet(false, true))
            return List.of(Component.translatable(KEY + "busy"));

        var mc = Minecraft.getInstance();
        ProfileEnvironment environment;
        Runnable undo;
        try {
            environment = ProfileEnvironment.capture();
            undo = scene.setUp();
        } catch (RuntimeException e) {
            BUSY.set(false);
            Library.LOGGER.error(e, "Unable to set up the profile scene %s", scene.commandName());
            return List.of(Component.translatable(KEY + "setupfailed", e.toString()));
        }

        var lines = new ArrayList<Component>();
        lines.add(Component.translatable(KEY + "started", scene.commandName(), scene.what(), scene.settle(), seconds, scene.tip()));
        if (!scene.commands().isEmpty()) {
            var list = "/" + String.join(", /", scene.commands());
            var connection = mc.getConnection();
            if (mc.hasSingleplayerServer() && connection != null) {
                scene.commands().forEach(connection::sendCommand);
                lines.add(Component.translatable(KEY + "sent", list));
            } else {
                lines.add(Component.translatable(KEY + "notsent", list));
            }
        }

        var folder = ContainerManager.resolve(IMinecraftDirectories.class).getModProfileDirectory();
        var file = folder.resolve(ProfileFiles.name(scene, LocalDateTime.now()));
        state = State.WAITING;
        until = System.nanoTime() + scene.settle() * 1_000_000_000L;
        var thread = new Thread(() -> {
            try {
                record(scene, file, seconds, environment);
            } finally {
                state = State.IDLE;
                mc.execute(() -> {
                    undo.run();
                    BUSY.set(false);
                });
            }
        }, "Dynamic Surroundings profile");
        thread.setDaemon(true);
        thread.start();
        return lines;
    }

    private static void record(ProfileScene scene, Path file, int seconds, ProfileEnvironment environment) {
        try {
            Thread.sleep(scene.settle() * 1000L);
            state = State.RECORDING;
            until = System.nanoTime() + seconds * 1_000_000_000L;
            ProfileRecording.record(file, Duration.ofSeconds(seconds), environment);
            // A summary to paste where a player asks for help; the recording is still sent without one
            String text = null;
            ProfileHeadline headline = null;
            try {
                var summary = ProfileSummary.of(file);
                text = summary.text(scene.commandName());
                headline = summary.headline().orElse(null);
            } catch (IOException | RuntimeException e) {
                Library.LOGGER.warn("Unable to summarize the profile %s: %s", file.getFileName(), e);
            }
            var zip = ProfileFiles.zip(file, text);
            var summaryText = text;
            var summaryHeadline = headline;
            Minecraft.getInstance().execute(() -> report(zip, summaryText, summaryHeadline));
        } catch (Throwable t) {
            Library.LOGGER.error(t, "Unable to record a profile");
            tell(Component.translatable(KEY + "failed", t.toString()));
        }
    }

    /**
     * Says where the recording is, the name opening it when clicked, with the frame rate, the costliest effect and a
     * button copying the summary, on the render thread; and logs it, with the summary and the render timers that
     * measured lately
     */
    private static void report(Path file, @Nullable String summary, @Nullable ProfileHeadline headline) {
        var name = Component.literal(file.getFileName().toString()).withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.toString()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(file.toString()))));
        var fps = Minecraft.getInstance().getFps();
        tell(Component.translatable(KEY + "done", name, fps));
        Library.LOGGER.info("Profile recorded to %s (%d FPS)", file, fps);
        for (var timer : RenderTimers.ALL) {
            if (timer.isStale())
                continue;
            Library.LOGGER.info("  %s: GPU %.3f ms (peak %.3f), CPU %.3f ms", timer.name(), timer.getMSecs(), timer.getPeakMSecs(), timer.getCpuMSecs());
        }
        if (headline != null)
            tell(Component.translatable(KEY + "headline", headline.fps(), headline.effect(), percent(headline.gpuShare()),
                    percent(headline.cpuShare())));
        if (summary != null) {
            Library.LOGGER.info("Profile summary:%n%s", summary);
            tell(Component.translatable(KEY + "copy").withStyle(Style.EMPTY
                    .withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, summary))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(KEY + "copy.tooltip")))));
        }
    }

    private static String percent(double share) {
        return String.format(Locale.ROOT, "%.0f%%", share * 100D);
    }

    private static void tell(Component message) {
        Minecraft.getInstance().execute(() -> GameUtils.getPlayer().ifPresent(p -> p.sendSystemMessage(message)));
    }
}

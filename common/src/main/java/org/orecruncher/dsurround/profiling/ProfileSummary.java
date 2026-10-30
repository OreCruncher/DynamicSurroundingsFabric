package org.orecruncher.dsurround.profiling;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * A short summary of a recording, for players to paste where they ask for help (an issue, Discord) when sending the
 * recording itself is too much: what it was made on, the frames, what each effect cost, the client ticks, GC, and the
 * settings that aren't as they come. In English, for the mod's authors; kept to {@link #LIMIT} characters, a Discord
 * message's worth, dropping the cheapest effects and the last changed settings if it must. Made from the finished
 * recording, which holds nothing private (see {@link ProfileRecording}), so neither can this. Uses {@code jdk.jfr}:
 * only loaded once a recording has been made.
 */
final class ProfileSummary {

    /**
     * The most characters a summary has: a Discord message holds 2000
     */
    static final int LIMIT = 1900;
    // An effect that cost less than this, GPU and CPU together, in ms a frame, drew nothing to speak of
    private static final double TRIVIAL_MS = 0.01;
    private static final double TICKS_PER_SECOND = 20D;
    // Handlers named in the ticks' line
    private static final int TOP_HANDLERS = 3;

    // What the recording was made on, by the Environment event's field names
    private final Map<String, Object> environment = new LinkedHashMap<>();
    // Each second's frames
    private final List<FrameSecond> frames = new ArrayList<>();
    // Each effect's averages, as written once a second
    private final Map<String, List<double[]>> timers = new LinkedHashMap<>();
    // The effects timed once a tick rather than once a frame
    private final Set<String> perTick = new HashSet<>();
    // Each tick's handlers' time, ns
    private final Map<Long, Long> ticks = new TreeMap<>();
    private final Map<String, Long> handlerTotals = new HashMap<>();
    private final List<Double> pauses = new ArrayList<>();
    private final List<String> hooksMissing = new ArrayList<>();
    private final List<String> standingAside = new ArrayList<>();
    private final List<String> changed = new ArrayList<>();

    record FrameSecond(int frames, double mean, double p95, double max, double gpu, double cpu) {
    }

    /**
     * Reads a finished recording
     */
    static ProfileSummary of(Path recording) throws IOException {
        var summary = new ProfileSummary();
        try (var file = new RecordingFile(recording)) {
            while (file.hasMoreEvents())
                summary.add(file.readEvent());
        }
        return summary;
    }

    private void add(RecordedEvent event) {
        switch (event.getEventType().getName()) {
            case "dsurround.Environment" -> {
                for (var field : event.getFields())
                    if (!field.getName().equals("startTime") && !field.getName().equals("duration")
                            && !field.getName().equals("eventThread") && !field.getName().equals("stackTrace"))
                        this.environment.put(field.getName(), event.getValue(field.getName()));
            }
            case "dsurround.Frames" -> this.frame(new FrameSecond(event.getInt("frames"), event.getDouble("mean"),
                    event.getDouble("p95"), event.getDouble("max"), event.getDouble("gpu"), event.getDouble("cpu")));
            case "dsurround.RenderTimer" -> this.timer(event.getString("effect"), event.getDouble("gpuAverage"),
                    event.getDouble("cpuAverage"), event.getBoolean("perTick"));
            case "dsurround.HandlerTick" -> this.handlerTick(event.getLong("tick"), event.getString("handler"), event.getLong("time"));
            case "jdk.GCPhasePause" -> this.pause(event.getDuration("duration").toNanos() / 1_000_000D);
            case "dsurround.Feature" -> this.feature(event.getString("kind"), event.getString("name"), event.getString("state"),
                    event.getBoolean("usual"));
            default -> {
            }
        }
    }

    void environment(String field, Object value) {
        this.environment.put(field, value);
    }

    void frame(FrameSecond second) {
        this.frames.add(second);
    }

    void timer(String effect, double gpu, double cpu) {
        this.timer(effect, gpu, cpu, false);
    }

    /**
     * @param perTick whether the effect is timed once a client tick rather than once a frame (see
     *                {@link org.orecruncher.dsurround.effects.RenderTimer#isPerTick})
     */
    void timer(String effect, double gpu, double cpu, boolean perTick) {
        this.timers.computeIfAbsent(effect, k -> new ArrayList<>()).add(new double[]{gpu, cpu});
        if (perTick)
            this.perTick.add(effect);
    }

    void handlerTick(long tick, String handler, long nanos) {
        this.ticks.merge(tick, nanos, Long::sum);
        this.handlerTotals.merge(handler, nanos, Long::sum);
    }

    void pause(double ms) {
        this.pauses.add(ms);
    }

    void feature(String kind, String name, String state, boolean usual) {
        if (usual)
            return;
        switch (kind) {
            case ProfileFeature.HOOK -> this.hooksMissing.add(name);
            case ProfileFeature.CONFLICT -> this.standingAside.add(name + " (" + state + ")");
            case ProfileFeature.SETTING -> this.changed.add(name + "=" + state);
            default -> {
            }
        }
    }

    /**
     * Drawing a frame, on the GPU and CPU, ms: the seconds' means; zeros without frames
     */
    private double[] drawing() {
        if (this.frames.isEmpty())
            return new double[]{0D, 0D};
        double gpu = 0D, cpu = 0D;
        for (var second : this.frames) {
            gpu += second.gpu();
            cpu += second.cpu();
        }
        return new double[]{gpu / this.frames.size(), cpu / this.frames.size()};
    }

    private int fps() {
        long count = 0;
        double total = 0D;
        for (var second : this.frames) {
            count += second.frames();
            total += second.mean() * second.frames();
        }
        return count == 0 || total <= 0D ? 0 : (int) Math.round(1000D * count / total);
    }

    /**
     * The effects that drew something, the costliest first: name, GPU and CPU ms. A part ("- " name, following its
     * effect) is in its effect's time and left out, unless it took longer than the effect, which it then can't be part
     * of, or is timed once a tick (the clouds' field, worked out on the tick, not while they are drawn): that is named
     * for its effect. One timed once a tick is given as its time a frame (20 ticks a second spread over the frames), to
     * compare with the rest.
     */
    private List<Map.Entry<String, double[]>> effects() {
        var effects = new ArrayList<Map.Entry<String, double[]>>();
        String effect = "";
        double[] effectMeans = {0D, 0D};
        int fps = this.fps();
        for (var entry : this.timers.entrySet()) {
            double gpu = 0D, cpu = 0D;
            for (var row : entry.getValue()) {
                gpu += row[0];
                cpu += row[1];
            }
            int n = entry.getValue().size();
            var means = new double[]{gpu / n, cpu / n};
            String name = entry.getKey();
            if (this.perTick.contains(name) && fps > 0) {
                means[0] *= TICKS_PER_SECOND / fps;
                means[1] *= TICKS_PER_SECOND / fps;
            }
            if (name.startsWith("- ")) {
                if (!this.perTick.contains(entry.getKey()) && means[0] <= effectMeans[0] && means[1] <= effectMeans[1])
                    continue;
                name = effect + " " + name.substring(2).toLowerCase(Locale.ROOT);
            } else {
                effect = name;
                effectMeans = means;
            }
            if (means[0] + means[1] >= TRIVIAL_MS)
                effects.add(Map.entry(name, means));
        }
        effects.sort(Comparator.comparingDouble((Map.Entry<String, double[]> e) -> e.getValue()[0] + e.getValue()[1]).reversed());
        return effects;
    }

    /**
     * The frame rate and the costliest effect, as a share of drawing a frame; none without frames or effects
     */
    Optional<ProfileHeadline> headline() {
        var drawing = this.drawing();
        if (this.frames.isEmpty() || drawing[0] <= 0D && drawing[1] <= 0D)
            return Optional.empty();
        ProfileHeadline best = null;
        for (var effect : this.effects()) {
            double gpuShare = share(effect.getValue()[0], drawing[0]);
            double cpuShare = share(effect.getValue()[1], drawing[1]);
            if (best == null || Math.max(gpuShare, cpuShare) > Math.max(best.gpuShare(), best.cpuShare()))
                best = new ProfileHeadline(this.fps(), effect.getKey(), gpuShare, cpuShare);
        }
        return Optional.ofNullable(best);
    }

    private static double share(double part, double whole) {
        return whole > 0D ? part / whole : 0D;
    }

    /**
     * The summary, at most {@link #LIMIT} characters
     *
     * @param scene the profile's scene, as the command names it
     */
    String text(String scene) {
        var lines = new ArrayList<String>();
        var e = this.environment;
        int seconds = this.frames.isEmpty() ? this.ticks.size() / 20 : this.frames.size();
        lines.add(f("Dynamic Surroundings profile: %s, %d s", scene, seconds));
        lines.add(f("%s, %s mods; Java %s; %s", e.getOrDefault("mod", "?"), e.getOrDefault("mods", "?"),
                e.getOrDefault("java", "?"), e.getOrDefault("os", "?")));
        // As the graphics device reports itself (see ProfileEnvironment)
        lines.add(f("GPU: %s (%s; %s, driver %s)", e.getOrDefault("gpu", "?"), e.getOrDefault("gpuType", "?"),
                e.getOrDefault("backend", "?"), e.getOrDefault("driver", "?")));
        var display = f("Display: %sx%s, %s, render distance %s, vsync %s, frame limit %s", e.getOrDefault("width", "?"),
                e.getOrDefault("height", "?"), e.getOrDefault("graphics", "?"), e.getOrDefault("renderDistance", "?"),
                Boolean.TRUE.equals(e.get("vsync")) ? "on" : "off", limit(e.get("frameRateLimit")));
        if (Boolean.TRUE.equals(e.get("shaderPack")))
            display += ", shader pack";
        if (e.get("devRun") instanceof String run && !run.isEmpty())
            display += ", dev run " + run;
        lines.add(display);

        var drawing = this.drawing();
        if (!this.frames.isEmpty()) {
            var p95s = this.frames.stream().mapToDouble(FrameSecond::p95).sorted().toArray();
            double longest = this.frames.stream().mapToDouble(FrameSecond::max).max().orElse(0D);
            double mean = this.fps() > 0 ? 1000D / this.fps() : 0D;
            lines.add(f("Frames: %d fps, mean %.1f ms, p95 %.1f (worst second %.1f), longest %.1f; drawing GPU %.2f ms, CPU %.2f ms",
                    this.fps(), mean, p95s[p95s.length / 2], p95s[p95s.length - 1], longest, drawing[0], drawing[1]));
        }

        if (!this.ticks.isEmpty()) {
            var totals = this.ticks.values().stream().mapToDouble(n -> n / 1_000_000D).sorted().toArray();
            double mean = 0D;
            for (var t : totals)
                mean += t;
            mean /= totals.length;
            var top = this.handlerTotals.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(TOP_HANDLERS)
                    .map(h -> f("%s %.2f", h.getKey(), h.getValue() / 1_000_000D / totals.length)).toList();
            lines.add(f("Ticks: mean %.2f ms, p95 %.2f; most: %s", mean, totals[Math.min(totals.length - 1, (int) (totals.length * 0.95))],
                    String.join(", ", top)));
        }

        if (!this.pauses.isEmpty()) {
            var sorted = this.pauses.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            lines.add(f("GC: %d pauses, median %.1f ms, longest %.1f ms", sorted.length, sorted[sorted.length / 2], sorted[sorted.length - 1]));
        }
        if (!this.hooksMissing.isEmpty())
            lines.add("Hooks not applied: " + String.join(", ", this.hooksMissing));
        if (!this.standingAside.isEmpty())
            lines.add("Standing aside: " + String.join(", ", this.standingAside));

        // The effects after what is above, then the changed settings, each as many as fit
        var effects = this.effects().stream().map(entry -> {
            var ms = entry.getValue();
            return this.frames.isEmpty()
                    ? f("%s %.2f/%.2f", entry.getKey(), ms[0], ms[1])
                    : f("%s %.2f/%.2f (%.0f%%/%.0f%%)", entry.getKey(), ms[0], ms[1], 100D * share(ms[0], drawing[0]), 100D * share(ms[1], drawing[1]));
        }).toList();
        int fixed = length(lines);
        var effectsLine = fit("Effects (ms GPU/CPU" + (this.frames.isEmpty() ? "" : ", share of drawing") + "): ", effects,
                LIMIT - fixed - 1 - minimum("Changed: ", this.changed));
        // After the frames, which they are shares of
        if (effectsLine != null)
            lines.add(this.frames.isEmpty() ? 4 : 5, effectsLine);
        var changedLine = fit("Changed: ", this.changed, LIMIT - length(lines) - 1);
        if (changedLine != null)
            lines.add(changedLine);
        return String.join("\n", lines);
    }

    private static String limit(@Nullable Object frameRateLimit) {
        // The video setting's top, 260, is no limit
        return frameRateLimit instanceof Number n && n.intValue() >= 260 ? "none" : String.valueOf(frameRateLimit);
    }

    private static int length(List<String> lines) {
        int length = 0;
        for (var line : lines)
            length += line.length() + 1;
        return Math.max(0, length - 1);
    }

    // The room a list's line needs at the least: its title and "+N more"
    private static int minimum(String title, List<String> items) {
        return items.isEmpty() ? 0 : title.length() + 12;
    }

    /**
     * A line of as many of the items as fit in {@code room} characters, saying how many more there are; null if there
     * are none, or not even the title fits
     */
    @Nullable
    private static String fit(String title, List<String> items, int room) {
        if (items.isEmpty() || room < title.length() + 12)
            return null;
        var line = new StringBuilder(title);
        int shown = 0;
        for (var item : items) {
            int left = items.size() - shown - 1;
            var more = left > 0 ? f(", +%d more", left).length() : 0;
            if (line.length() + (shown > 0 ? 2 : 0) + item.length() + more > room)
                break;
            if (shown > 0)
                line.append(", ");
            line.append(item);
            shown++;
        }
        if (shown < items.size())
            line.append(shown > 0 ? ", " : "").append(f("+%d more", items.size() - shown));
        return line.toString();
    }

    private static String f(String format, Object... args) {
        return String.format(Locale.ROOT, format, args);
    }
}

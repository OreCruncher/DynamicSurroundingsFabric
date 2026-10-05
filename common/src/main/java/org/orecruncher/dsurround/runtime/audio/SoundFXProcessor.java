package org.orecruncher.dsurround.runtime.audio;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IClientTickStart;
import org.orecruncher.dsurround.eventing.ICollectDiagnostics;
import org.orecruncher.dsurround.lib.function.SingletonSupplier;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.LogThrottle;
import org.orecruncher.dsurround.lib.threading.Tasks;
import org.orecruncher.dsurround.lib.threading.Worker;
import org.orecruncher.dsurround.runtime.audio.effects.Efx;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReferenceArray;

public final class SoundFXProcessor {

    private static final IModLog LOGGER = ContainerManager.memoize(IModLog.class);
    private static final int SOUND_PROCESS_ITERATION = 1000 / 20;   // Match MC client tick rate
    private static final LogThrottle<Object> TASK_ERRORS = LogThrottle.firstN(LOGGER, "enhanced sound task errors", null, 10);

    static boolean isAvailable;

    // The context of the sound playing on each OpenAL source, indexed by source ID - 1. An atomic array so the
    // worker thread sees each context fully set up: contexts are stored by the client thread and read by the sound
    // engine and worker threads. Each entry belongs to one sound at a time: its stop hook clears it before the
    // source is deleted, so before the ID can be reused. Replaced when the sound engine starts, null while stopped.
    private static volatile AtomicReferenceArray<SourceContext> sources;

    private static Worker soundProcessor;
    private static String diagnosticString = StringUtils.EMPTY;

    // Use our own thread pool avoiding the common pool.  Thread allocation is better controlled, and we won't run
    // into/cause any problems with other tasks in the common pool.
    private static final SingletonSupplier<ExecutorService> threadPool = SingletonSupplier.from(() -> {
        var config = ContainerManager.resolve(Configuration.EnhancedSounds.class);
        int threads = config.backgroundThreadWorkers;
        if (threads == 0)
            threads = 2;
        LOGGER.info("Threads allocated to enhanced sound processor: %d", threads);
        // Named daemon threads: identifiable in thread dumps and profilers, and they can't keep the game's process
        // alive after it exits. The pool lives as long as the game.
        return Executors.newFixedThreadPool(threads, Worker.threadFactory("Enhanced Sound Task"));
    });

    private static WorldContext worldContext = new WorldContext();

    static {
        ICollectDiagnostics.EVENT.register(SoundFXProcessor::onGatherText);
        IClientTickStart.EVENT.register(SoundFXProcessor::clientTick);
        // Lets sounds reuse their ray traced results until something in the world changes
        WorldChangeTracker.register();
    }

    public static WorldContext getWorldContext() {
        return worldContext;
    }

    /**
     * Indicates if the SoundFX feature is available.
     *
     * @return true if the feature is available, false otherwise.
     */
    public static boolean isAvailable() {
        return isAvailable;
    }

    public static void initialize() {
        Efx.initialize();

        sources = new AtomicReferenceArray<>(AudioUtilities.getMaxSounds());

        if (soundProcessor == null) {
            soundProcessor = new Worker(
                    "Enhanced Sound Processor",
                    SoundFXProcessor::processSounds,
                    SOUND_PROCESS_ITERATION,
                    LOGGER
            );
            soundProcessor.start();
        }

        isAvailable = true;
    }

    public static void deinitialize() {
        if (isAvailable()) {
            isAvailable = false;
            if (soundProcessor != null) {
                // Waits for a run in progress to finish, so it isn't using the sources or effects cleared below
                soundProcessor.stop();
                soundProcessor = null;
            }
            sources = null;
            Efx.deinitialize();
        }
    }

    /**
     * The context of the sound playing on the channel's source, or null if none (or the processor is stopped).
     */
    private static SourceContext contextFor(final Channel channel) {
        final var current = sources;
        return current == null ? null : current.get(channel.source - 1);
    }

    private static void setContext(final int sourceId, final SourceContext ctx) {
        final var current = sources;
        if (current != null)
            current.set(sourceId - 1, ctx);
    }

    private static boolean shouldIgnoreSound(SoundInstance sound) {
        return sound.isRelative()
                || sound.getAttenuation() == SoundInstance.Attenuation.NONE
                || sound.getSource() == SoundSource.MASTER
                || sound.getSource() == SoundSource.MUSIC
                || sound.getSource() == SoundSource.WEATHER;
    }

    /**
     * Callback hook from an injection.  This callback is made on the client thread after the sound source
     * is created, but before it is configured.
     *
     * @param sound The sound that is going to play
     * @param entry The ChannelManager.Entry instance for the sound play
     */
    public static void onSoundPlay(final SoundInstance sound, final ChannelAccess.ChannelHandle entry) {

        if (!isAvailable() || shouldIgnoreSound(sound))
            return;

        assert entry.channel != null;
        int id = entry.channel.source;
        if (id > 0) {
            final SourceContext ctx = new SourceContext(id);
            ctx.attachSound(sound);
            ctx.enable();
            setContext(id, ctx);
        }
    }

    /**
     * Invoked when the sound source is played.  This will cause the environment to be evaluated
     * before the sound instance is processed.
     */
    public static void onSourcePlay(final Channel source) {
        if (!isAvailable())
            return;

        final SourceContext context = contextFor(source);
        if (context != null) {
            context.exec();
        }
    }

    /**
     * Callback hook from an injection.  Will be invoked by the sound processing thread when checking status, which
     * essentially is a "tick".
     *
     * @param source SoundSource being ticked
     */
    public static void tick(final Channel source) {
        if (!isAvailable())
            return;

        final SourceContext context = contextFor(source);
        if (context != null) {
            context.tick();
        }
    }

    /**
     * Injected into SoundSource and will be invoked when a sound source is being terminated.
     *
     * @param source The sound source that is stopping
     */
    public static void stopSoundPlay(final Channel source) {
        if (!isAvailable())
            return;

        setContext(source.source, null);
    }

    /**
     * Invoked on a client tick. Establishes the current world context for further computation..
     */
    public static void clientTick(Minecraft client) {
        if (isAvailable()) {
            worldContext = new WorldContext();
        }
    }

    /**
     * Separate thread for evaluating the environment for the sound play.  These routines can get a little heavy
     * so offloading to a separate thread to keep it out of either the client tick or sound engine makes sense.
     */
    private static void processSounds() {
        try {
            final ExecutorService pool = threadPool.get();
            assert pool != null;

            // Read once: deinitialize() may clear it from the client thread
            final AtomicReferenceArray<SourceContext> current = sources;
            if (current == null)
                return;

            final ObjectArray<Future<?>> tasks = new ObjectArray<>(current.length());

            // Each source will be examined once per 7 ticks. See
            // SourceContext.UPDATE_FREQUENCY_TICKS for the current interval.
            for (int i = 0; i < current.length(); i++) {
                final SourceContext ctx = current.get(i);
                if (ctx != null && ctx.shouldExecute()) {
                    tasks.add(pool.submit(ctx));
                }
            }

            diagnosticString = "(ticked: %d)".formatted(tasks.size());

            // SourceContext catches and logs its own exceptions, so a task failing here threw an Error (a game class
            // changed under it, say)
            Tasks.awaitAll(tasks, TASK_ERRORS, "An enhanced sound task");

        } catch (final Throwable t) {
            LOGGER.error(t, "Error in the enhanced sound processor");
        }
    }

    /**
     * Gather diagnostics for the display
     */
    private static void onGatherText(CollectDiagnosticsEvent event ) {
        if (isAvailable() && soundProcessor != null) {
            final String msg = soundProcessor.getDiagnosticString() + " " + diagnosticString;
            event.add(CollectDiagnosticsEvent.Section.Systems, msg);
        } else {
            event.getSectionText(CollectDiagnosticsEvent.Section.Systems).add(Component.literal("Enhanced sound processing disabled"));
        }
    }
}

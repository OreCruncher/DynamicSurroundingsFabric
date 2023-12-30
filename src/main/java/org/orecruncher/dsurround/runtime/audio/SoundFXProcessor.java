package org.orecruncher.dsurround.runtime.audio;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.lib.Singleton;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.threading.Worker;
import org.orecruncher.dsurround.mixins.audio.MixinSourceManagerAccessor;
import org.orecruncher.dsurround.runtime.audio.effects.Effects;
import org.orecruncher.dsurround.mixinutils.ISourceContext;

import java.util.Arrays;
import java.util.concurrent.*;

public final class SoundFXProcessor {

    private static final IModLog LOGGER = ContainerManager.resolve(IModLog.class);
    private static final int SOUND_PROCESS_ITERATION = 1000 / 20;   // Match MC client tick rate

    static boolean isAvailable;
    // Sparse array to hold references to the SoundContexts of playing sounds
    private static SourceContext[] sources;
    private static Worker soundProcessor;

    // Use our own thread pool avoiding the common pool.  Thread allocation is better controlled, and we won't run
    // into/cause any problems with other tasks in the common pool.
    private static final Singleton<ExecutorService> threadPool = new Singleton<>(() -> {
        var config = ContainerManager.resolve(Configuration.EnhancedSounds.class);
        int threads = config.backgroundThreadWorkers;
        if (threads == 0)
            threads = 2;
        LOGGER.info("Threads allocated to enhanced sound processor: %d", threads);
        return Executors.newFixedThreadPool(threads);
    });

    private static WorldContext worldContext = new WorldContext();

    static {
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(SoundFXProcessor::onGatherText);
        ClientTickEvents.START_CLIENT_TICK.register(SoundFXProcessor::clientTick);
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
        Effects.initialize();

        sources = new SourceContext[AudioUtilities.getMaxSounds()];

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
                soundProcessor.stop();
                soundProcessor = null;
            }
            if (sources != null) {
                Arrays.fill(sources, null);
                sources = null;
            }
            Effects.deinitialize();
        }
    }

    private static boolean shouldIgnoreSound(SoundInstance sound) {
        return sound.isRelative()
                || sound.getAttenuationType() == SoundInstance.AttenuationType.NONE
                || sound.getCategory() == SoundCategory.MASTER
                || sound.getCategory() == SoundCategory.MUSIC
                || sound.getCategory() == SoundCategory.WEATHER;
    }

    /**
     * Callback hook from an injection.  This callback is made on the client thread after the sound source
     * is created, but before it is configured.
     *
     * @param sound The sound that is going to play
     * @param entry The ChannelManager.Entry instance for the sound play
     */
    public static void onSoundPlay(final SoundInstance sound, final Channel.SourceManager entry) {

        if (!isAvailable())
            return;

        if (shouldIgnoreSound(sound))
            return;

        ISourceContext source = (ISourceContext)(((MixinSourceManagerAccessor) entry).dsurround_getSource());
        assert source != null;
        int id = source.dsurround_getId();
        if (id > 0) {
            final SourceContext ctx = new SourceContext(id);
            ctx.attachSound(sound);
            ctx.enable();
            source.dsurround_setData(ctx);
        }
    }

    /**
     * Invoked when the sound source is played.  This will cause the environment to be evaluated
     * before the sound instance is processed.
     */
    public static void onSourcePlay(final Source source) {
        var context = (ISourceContext) source;
        var data = context.dsurround_getData();
        data.ifPresent(ctx -> {
            var id = ctx.getId();
            ctx.exec();
            sources[id - 1] = ctx;
        });
    }

    /**
     * Callback hook from an injection.  Will be invoked by the sound processing thread when checking status which
     * essentially is a "tick".
     *
     * @param source SoundSource being ticked
     */
    public static void tick(final Source source) {
        var src = (ISourceContext) source;
        var data = src.dsurround_getData();
        data.ifPresent(SourceContext::tick);
    }

    /**
     * Injected into SoundSource and will be invoked when a sound source is being terminated.
     *
     * @param source The sound source that is stopping
     */
    public static void stopSoundPlay(final Source source) {
        var sourceContext = (ISourceContext) source;
        var data = sourceContext.dsurround_getData();
        data.ifPresent(sc -> sources[sc.getId()] = null);
    }

    /**
     * Injected into SoundSource and will be invoked when a non-streaming sound data stream is attached to the
     * SoundSource.  Take the opportunity to convert the audio stream into mono format if needed.  Note that
     * conversion will take place only if it is enabled in the configuration and the sound is playing
     * non-attenuated.
     *
     * @param source SoundSource for which the audio buffer is being generated
     * @param buffer The buffer in question.
     */

    public static void doMonoConversion(final Source source, final StaticSound buffer) {

        // If disabled return
        if (!Client.Config.enhancedSounds.enableMonoConversion)
            return;

        var data = ((ISourceContext) source).dsurround_getData();
        data.ifPresent(ctx ->{
            var s = ctx.getSound();
            if (s != null && s.getAttenuationType() != SoundInstance.AttenuationType.NONE && !s.isRelative())
                Conversion.convert(buffer);
        });
    }

    /**
     * Invoked on a client tick. Establishes the current world context for further computation..
     */
    public static void clientTick(MinecraftClient client) {
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

            final ObjectArray<Future<?>> tasks = new ObjectArray<>(sources.length);
            for (final SourceContext ctx : sources) {
                if (ctx != null && ctx.shouldExecute()) {
                    tasks.add(pool.submit(ctx));
                }
            }

            for (int i = 0; i < tasks.size(); i++)
                try {
                    tasks.get(i).get();
                } catch (InterruptedException | ExecutionException ignored) {
                }

        } catch (final Throwable t) {
            LOGGER.error(t, "Error in SoundContext ForkJoinPool");
        }
    }

    /**
     * Gather diagnostics for the display
     */
    private static void onGatherText(ClientEventHooks.CollectDiagnosticsEvent event ) {
        if (isAvailable() && soundProcessor != null) {
            final String msg = soundProcessor.getDiagnosticString();
            if (!StringUtils.isEmpty(msg))
                event.left.add(Formatting.GREEN + msg);
        }
    }
}

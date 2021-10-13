package org.orecruncher.dsurround.runtime.audio;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.lib.Singleton;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.math.TimerEMA;
import org.orecruncher.dsurround.lib.threading.Worker;
import org.orecruncher.dsurround.runtime.audio.effects.Effects;
import org.orecruncher.dsurround.xface.ISourceContext;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

public final class SoundFXProcessor {

    private static final IModLog LOGGER = Client.LOGGER.createChild(SoundFXProcessor.class);
    private static final int SOUND_PROCESS_ITERATION = 1000 / 20;   // Match MC client tick rate

    static boolean isAvailable;
    // Sparse array to hold references to the SoundContexts of playing sounds
    private static SourceContext[] sources;
    private static Worker soundProcessor;

    // Use our own thread pool avoiding the common pool.  Thread allocation is better controlled, and we won't run
    // into/cause any problems with other tasks in the common pool.
    private static final Singleton<ExecutorService> threadPool = new Singleton<>(() -> {
        int threads = Client.Config.enhancedSounds.backgroundThreadWorkers;
        if (threads == 0)
            threads = 2;
        LOGGER.info("Threads allocated to SoundControl sound processor: %d", threads);
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
                    "SoundControl Sound Processor",
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

        // Double suplex!  Queue the operation on the sound executor to do the config work.  This should queue in
        // behind any attempt at getting a sound source.
        entry.run(source -> {
            var id = ((ISourceContext) source).getId();
            if (id > 0) {
                final SourceContext ctx = new SourceContext();
                ctx.attachSound(sound);
                ctx.enable();
                ctx.exec();
                ((ISourceContext) source).setData(ctx);
                sources[id - 1] = ctx;
            }
        });
    }

    /**
     * Callback hook from an injection.  Will be invoked by the sound processing thread when checking status which
     * essentially is a "tick".
     *
     * @param source SoundSource being ticked
     */
    public static void tick(final Source source) {
        final SourceContext ctx = ((ISourceContext) source).getData();
        if (ctx != null)
            ctx.tick(((ISourceContext) source).getId());
    }

    /**
     * Injected into SoundSource and will be invoked when a sound source is being terminated.
     *
     * @param source The sound source that is stopping
     */
    public static void stopSoundPlay(final Source source) {
        final SourceContext ctx = ((ISourceContext) source).getData();
        if (ctx != null)
            sources[((ISourceContext) source).getId() - 1] = null;
    }

    /**
     * Injected into SoundSource and will be invoked when a non-streaming sound data stream is attached to the
     * SoundSource.  Take the opportunity to convert the audio stream into mono format if needed.
     *
     * @param source SoundSource for which the audio buffer is being generated
     * @param buffer The buffer in question.
     */

    public static void doMonoConversion(final Source source, final StaticSound buffer) {

        // If disabled return
        if (!Client.Config.enhancedSounds.enableMonoConversion)
            return;

        final SourceContext ctx = ((ISourceContext) source).getData();

        // If there is no context attached and conversion is enabled do it.  This can happen if enhanced sound
        // processing is turned off.  If there is a context, make sure that the sound is attenuated.
        boolean doConversion = ctx == null || (ctx.getSound() != null && ctx.getSound().getAttenuationType() != SoundInstance.AttenuationType.NONE);

        if (doConversion)
            Conversion.convert(buffer);
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
            final ObjectArray<Future<?>> tasks = new ObjectArray<>(256);
            for (int i = 0; i < AudioUtilities.getMaxSounds(); i++) {
                final SourceContext ctx = sources[i];
                if (ctx != null && ctx.shouldExecute()) {
                    tasks.add(pool.submit(ctx));
                }
            }

            if (tasks.size() > 0)
                tasks.forEach(t -> {
                    try {
                        t.get();
                    } catch (InterruptedException | ExecutionException ignored) {
                    }
                });
        } catch (final Throwable t) {
            LOGGER.error(t, "Error in SoundContext ForkJoinPool");
        }
    }

    /**
     * Gather diagnostics for the display
     */
    private static void onGatherText(Collection<String> left, Collection<String> right, Collection<TimerEMA> timerEMAS) {
        if (isAvailable() && soundProcessor != null) {
            final String msg = soundProcessor.getDiagnosticString();
            if (!StringUtils.isEmpty(msg))
                left.add(Formatting.GREEN + msg);
        }
    }

    /**
     * Validates that the current OpenAL state is not in error.  If in an error state an exception will be thrown.
     *
     * @param msg Optional message to be displayed along with error data
     */
    public static void validate(final String msg) {
        validate(() -> msg);
    }

    /**
     * Validates that the current OpenAL state is not in error.  If in an error state an exception will be thrown.
     *
     * @param err Supplier for the error message to post with exception info
     */
    public static void validate(@Nullable final Supplier<String> err) {
        final int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            String errorName = AL10.alGetString(error);
            if (StringUtils.isEmpty(errorName))
                errorName = Integer.toString(error);

            String msg = null;
            if (err != null)
                msg = err.get();
            if (msg == null)
                msg = "NONE";

            throw new IllegalStateException(String.format("OpenAL Error: %s [%s]", errorName, msg));
        }
    }
}
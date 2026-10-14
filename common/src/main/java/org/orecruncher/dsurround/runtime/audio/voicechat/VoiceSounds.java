package org.orecruncher.dsurround.runtime.audio.voicechat;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.ICollectDiagnostics;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.LogThrottle;
import org.orecruncher.dsurround.runtime.audio.ISourceProvider;
import org.orecruncher.dsurround.runtime.audio.EnhancedSoundProcessor;
import org.orecruncher.dsurround.runtime.audio.SourceProviders;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Enhanced sound processing (reverb, occlusion, air absorption) for Simple Voice Chat's voices: which channels are
 * playing, and when each voice's processing starts, follows it, and stops.
 * <p>
 * Voice chat plays voices in an OpenAL context of its own, on a device of its own; when it makes the context, the
 * {@link Backend} sets up the effects in it. Voice chat tells us about every packet it is about to play, on the
 * channel's speaker thread with its context current: there the voice's position is updated and changed filters are
 * uploaded. As a {@link ISourceProvider}, it hands the voices to the worker threads to recalculate, as the game's
 * sounds are (see {@link EnhancedSoundProcessor}).
 * <p>
 * Free of voice chat's classes, which only {@link VoiceChatPlugin} uses.
 */
public final class VoiceSounds implements ISourceProvider {

    /**
     * A channel not heard from for this long is forgotten. Its speaker stops after a few seconds of silence, and
     * plays on a new source when the player talks again.
     */
    static final long FORGET_AFTER_NANOS = TimeUnit.SECONDS.toNanos(5);

    /**
     * The OpenAL side: {@link OpenALBackend}, or a fake in tests.
     */
    interface Backend {
        /**
         * Sets up for voice chat's new context, current on this thread.
         *
         * @return what makes its voices, or null if they won't be processed (enhanced sounds are off, or setting up
         * failed)
         */
        @Nullable
        VoiceFactory open(long device);

        /**
         * Whether voices are processed now: the option is on and the sound processor running.
         */
        boolean isActive();
    }

    interface VoiceFactory {
        /**
         * A voice playing on the source, at the position.
         */
        Voice create(int sourceId, Vec3 position);

        /**
         * The reverb sends voices have in the context.
         */
        int sends();

        /**
         * Voice chat is about to destroy the context: silences what is still ringing in it. Does nothing unless the
         * context is current on this thread.
         */
        void close();
    }

    /**
     * One voice's processing. Its methods other than {@link #call} are called on its speaker thread.
     */
    interface Voice extends Callable<Void> {
        int sourceId();

        /**
         * Calculates its filters and uploads them, so it starts with them.
         */
        void start();

        /**
         * It moved; uploads what changed since the last upload.
         */
        void update(Vec3 position);

        /**
         * Takes its filters off: it plays on without them.
         */
        void stop();

        /**
         * Asked by the processor's thread each run: whether it is due a recalculation.
         */
        boolean shouldExecute();
    }

    private static final class Channel {
        final Voice voice;
        volatile long lastHeard;

        Channel(Voice voice, long now) {
            this.voice = voice;
            this.lastHeard = now;
        }
    }

    private final Backend backend;
    private final LongSupplier clock;
    private final LogThrottle<Object> errors;

    // Makes voices in voice chat's context, or null while it has none (or they aren't processed)
    private volatile VoiceFactory factory;
    private volatile boolean hasContext;
    private final Map<UUID, Channel> channels = new ConcurrentHashMap<>();

    VoiceSounds(Backend backend, LongSupplier clock, IModLog logger) {
        this.backend = backend;
        this.clock = clock;
        this.errors = LogThrottle.firstN(logger, "voice chat sound errors", null, 10);
    }

    /**
     * Processing through OpenAL, registered with the sound processor.
     */
    public static VoiceSounds create(IModLog logger) {
        var sounds = new VoiceSounds(new OpenALBackend(logger), System::nanoTime, logger);
        SourceProviders.register(sounds);
        ICollectDiagnostics.EVENT.register(event -> event.add(CollectDiagnosticsEvent.Section.Systems, sounds.diagnostics()));
        return sounds;
    }

    /**
     * Voice chat made its OpenAL context; it is current on this thread.
     */
    public void onContextCreated(long device) {
        this.factory = null;
        this.channels.clear();
        this.factory = this.backend.open(device);
        this.hasContext = true;
    }

    /**
     * Voice chat is about to destroy its OpenAL context, and the effects in it; current on this thread unless voice
     * chat didn't say which context it was. Its voices have stopped by now.
     */
    public void onContextDestroyed() {
        final var currentFactory = this.factory;
        this.hasContext = false;
        this.factory = null;
        this.channels.clear();
        if (currentFactory != null) {
            try {
                currentFactory.close();
            } catch (final Throwable t) {
                this.errors.error(t, "Unable to silence voice chat's effects");
            }
        }
    }

    /**
     * For the diagnostics overlay: whether voices are processed and how many are playing.
     */
    String diagnostics() {
        final var currentFactory = this.factory;
        if (currentFactory == null)
            return this.hasContext ? "Voice chat: not processed" : "Voice chat: not connected";
        if (!this.backend.isActive())
            return "Voice chat: processing off";
        return "Voice chat: %d voices (sends: %d)".formatted(this.channels.size(), currentFactory.sends());
    }

    /**
     * Voice chat is about to play a packet of a channel's audio. Called on the channel's speaker thread with voice
     * chat's context current.
     *
     * @param channelId the channel playing
     * @param sourceId  its OpenAL source
     * @param position  where it plays, or null if it isn't positional (a group's chat, say)
     */
    public void onSound(UUID channelId, int sourceId, @Nullable Vec3 position) {
        final var currentFactory = this.factory;
        if (currentFactory == null)
            return;

        try {
            var channel = this.channels.get(channelId);
            if (position == null || !this.backend.isActive()) {
                // Plays as voice chat set it up: take off any filters it had
                if (channel != null) {
                    this.channels.remove(channelId);
                    channel.voice.stop();
                }
                return;
            }

            if (channel == null || channel.voice.sourceId() != sourceId) {
                // New, or its speaker was remade with a new source
                var voice = currentFactory.create(sourceId, position);
                voice.start();
                this.channels.put(channelId, new Channel(voice, this.clock.getAsLong()));
            } else {
                channel.voice.update(position);
                channel.lastHeard = this.clock.getAsLong();
            }
        } catch (final Throwable t) {
            this.errors.error(t, "Unable to apply sound effects to a voice");
        }
    }

    /**
     * Hands over the voices due a recalculation, and forgets channels gone quiet.
     */
    @Override
    public void forEachDue(Consumer<Callable<?>> action) {
        if (this.channels.isEmpty())
            return;
        final long now = this.clock.getAsLong();
        this.channels.values().removeIf(channel -> now - channel.lastHeard > FORGET_AFTER_NANOS);
        for (var channel : this.channels.values()) {
            if (channel.voice.shouldExecute())
                action.accept(channel.voice);
        }
    }
}

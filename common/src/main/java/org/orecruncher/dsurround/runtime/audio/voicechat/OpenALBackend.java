package org.orecruncher.dsurround.runtime.audio.voicechat;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.SOFTHRTF;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.function.SingletonSupplier;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;
import org.orecruncher.dsurround.runtime.audio.EnhancedSoundProcessor;
import org.orecruncher.dsurround.runtime.audio.SourceContext;
import org.orecruncher.dsurround.runtime.audio.effects.Efx;

/**
 * Voice processing through OpenAL: the effects in voice chat's context, and a {@link SourceContext} per voice.
 */
final class OpenALBackend implements VoiceSounds.Backend {

    // Resolved on first use: voice chat may load its plugins before the mod's container is ready
    private static final SingletonSupplier<Configuration.EnhancedSounds> CONFIG = SingletonSupplier.from(() -> ContainerManager.resolve(Configuration.EnhancedSounds.class));

    // The longest leaving a world waits for muted reverbs to go quiet
    private static final long MAX_MUTE_WAIT_MILLIS = 100;

    private final IModLog logger;

    OpenALBackend(IModLog logger) {
        this.logger = logger;
    }

    @Override
    public @Nullable VoiceSounds.VoiceFactory open(long device) {
        if (!AudioUtilities.doEnhancedSounds())
            return null;
        try {
            int sends = this.requestSends(device);
            var efx = Efx.create(sends);
            this.logger.info("Enhanced sound processing for voice chat ready (aux sends: %d)", sends);
            return new VoiceSounds.VoiceFactory() {
                @Override
                public VoiceSounds.Voice create(int sourceId, Vec3 position) {
                    return new ContextVoice(sourceId, efx, position);
                }

                @Override
                public int sends() {
                    return efx.sends();
                }

                @Override
                public void close() {
                    if (!efx.isCurrent())
                        return;
                    efx.mute();
                    // Two updates: the one being mixed may have started before the mute
                    try {
                        Thread.sleep(Math.min(2 * efx.updateMillis(), MAX_MUTE_WAIT_MILLIS));
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            };
        } catch (final Throwable t) {
            this.logger.error(t, "Unable to set up enhanced sound processing for voice chat; voices will play without it");
            return null;
        }
    }

    @Override
    public boolean isActive() {
        return CONFIG.get().enableVoiceChatProcessing && EnhancedSoundProcessor.isAvailable();
    }

    /**
     * Voice chat makes its context without asking for auxiliary sends, which gets OpenAL Soft's default of 2. Asks
     * the device for one per reverb; a reset only changes the attributes given.
     *
     * @return the sends each source has
     */
    private int requestSends(long device) {
        int sends = ALC10.alcGetInteger(device, EXTEfx.ALC_MAX_AUXILIARY_SENDS);
        if (sends < Efx.SENDS && ALC10.alcIsExtensionPresent(device, "ALC_SOFT_HRTF")) {
            if (SOFTHRTF.alcResetDeviceSOFT(device, new int[]{EXTEfx.ALC_MAX_AUXILIARY_SENDS, Efx.SENDS, 0})) {
                sends = ALC10.alcGetInteger(device, EXTEfx.ALC_MAX_AUXILIARY_SENDS);
            } else {
                // Clear the error so voice chat doesn't find it
                int error = ALC10.alcGetError(device);
                this.logger.warn("Unable to raise voice chat's auxiliary sends (error %d); using %d", error, sends);
            }
        }
        return sends;
    }

    /**
     * A voice processed as a game sound is, from a {@link VoiceEmitter}.
     */
    private static final class ContextVoice implements VoiceSounds.Voice {
        private final VoiceEmitter emitter;
        private final SourceContext context;

        ContextVoice(int sourceId, Efx efx, Vec3 position) {
            this.emitter = new VoiceEmitter(position);
            this.context = new SourceContext(sourceId, efx, this.emitter);
        }

        @Override
        public int sourceId() {
            return this.context.getId();
        }

        @Override
        public void start() {
            this.context.exec();
        }

        @Override
        public void update(Vec3 position) {
            this.emitter.moveTo(position);
            this.context.tick();
        }

        @Override
        public void stop() {
            this.context.disable();
        }

        @Override
        public boolean shouldExecute() {
            return this.context.shouldExecute();
        }

        @Override
        public Void call() {
            return this.context.call();
        }
    }
}

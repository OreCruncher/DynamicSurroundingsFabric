package org.orecruncher.dsurround.runtime.audio;

import com.google.common.base.MoreObjects;
import net.minecraft.client.sound.SoundEngine;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.*;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.mixins.audio.MixinSoundEngineAccessor;

public final class AudioUtilities {
    private static final IModLog LOGGER = Client.LOGGER.createChild(AudioUtilities.class);

    private static final String[] HRTF_STATUS = {
            "ALC_HRTF_DISABLED_SOFT",
            "ALC_HRTF_ENABLED_SOFT",
            "ALC_HRTF_DENIED_SOFT",
            "ALC_HRTF_REQUIRED_SOFT",
            "ALC_HRTF_HEADPHONES_DETECTED_SOFT",
            "ALC_HRTF_UNSUPPORTED_FORMAT_SOFT"
    };

    private static int MAX_SOUNDS = 0;

    public static int getMaxSounds() {
        return MAX_SOUNDS;
    }

    public static float getMasterGain() {
        return GameUtils.getGameSettings().getSoundVolume(SoundCategory.MASTER);
    }

    /**
     * Provides a debug string for the specified sound object.
     *
     * @param sound Sound instance to provide a debug string for
     * @return Debug string
     */
    public static String debugString(@Nullable final SoundInstance sound) {

        if (sound == null)
            return "null";

        return MoreObjects.toStringHelper(sound)
                .addValue(sound.getId().toString())
                .addValue(sound.getCategory().toString())
                .addValue(sound.getAttenuationType().toString())
                .add("v", sound.getVolume())
                .add("p", sound.getPitch())
                .add("x", sound.getX())
                .add("y", sound.getY())
                .add("z", sound.getZ())
                .add("distance", sound.getSound().getAttenuation())
                .add("streaming", sound.getSound().isStreamed())
                .add("global", sound.isRelative())
                .toString();
    }

    /**
     * This method is invoked via the MixinSoundSystem injection.  It will be called when the sound system
     * is initialized, and it gives an opportunity to setup special effects processing.
     *
     * @param soundEngine The sound system instance being initialized
     */
    public static void initialize(final SoundEngine soundEngine) {

        MixinSoundEngineAccessor accessor = (MixinSoundEngineAccessor) soundEngine;

        try {

            final long device = accessor.getDevicePointer();

            boolean hasFX = false;
            if (doEnhancedSounds()) {
                LOGGER.info("Enhanced sounds are enabled.  Will perform sound engine reconfiguration.");
                final ALCCapabilities deviceCaps = ALC.createCapabilities(device);
                hasFX = deviceCaps.ALC_EXT_EFX;

                if (!hasFX) {
                    LOGGER.warn("EFX audio extensions not available for the current sound device!");
                } else {
                    // Using 4 aux slots instead of the default 2
                    final int[] attributes = new int[]{EXTEfx.ALC_MAX_AUXILIARY_SENDS, 4, 0};
                    final long ctx = ALC10.alcCreateContext(device, attributes);
                    ALC10.alcMakeContextCurrent(ctx);
                    accessor.setContextPointer(ctx);

                    // Have to re-enable since we reset the context
                    AL10.alEnable(EXTSourceDistanceModel.AL_SOURCE_DISTANCE_MODEL);

                    // If HRTF is available enable if configured to do so
                    if (deviceCaps.ALC_SOFT_HRTF) {
                        int status = ALC10.alcGetInteger(device, SOFTHRTF.ALC_HRTF_STATUS_SOFT);
                        LOGGER.info("HRTF status report before configuration: %s", HRTF_STATUS[status]);
                        if (status == SOFTHRTF.ALC_HRTF_DISABLED_SOFT && Client.Config.enhancedSounds.enableHRTF) {
                            final boolean result = SOFTHRTF.alcResetDeviceSOFT(device, new int[]{SOFTHRTF.ALC_HRTF_SOFT, ALC10.ALC_TRUE, 0});
                            if (result) {
                                status = ALC10.alcGetInteger(device, SOFTHRTF.ALC_HRTF_STATUS_SOFT);
                                LOGGER.warn("After configuration OpenAL reports HRTF status %s", HRTF_STATUS[status]);
                            } else {
                                LOGGER.warn("Unable to set HRTF feature in OpenAL");
                            }
                        } else {
                            LOGGER.info("HRTF is already configured or Dynamic Surroundings is not configured to enable");
                        }
                    }
                }
            }

            // Calculate the number of source slots available
            MAX_SOUNDS = ALC11.alcGetInteger(device, ALC11.ALC_MONO_SOURCES);

            // Do this last because it is dependent on the sound calculations
            if (hasFX)
                SoundFXProcessor.initialize();

            final String vendor = AL10.alGetString(AL10.AL_VENDOR);
            final String version = AL10.alGetString(AL10.AL_VERSION);
            final String renderer = AL10.alGetString(AL10.AL_RENDERER);
            final String extensions = AL10.alGetString(AL10.AL_EXTENSIONS);

            LOGGER.info("Vendor: %s", vendor);
            LOGGER.info("Version: %s", version);
            LOGGER.info("Renderer: %s", renderer);
            LOGGER.info("Extensions: %s", extensions);

        } catch (final Throwable t) {
            LOGGER.warn(t.getMessage());
            LOGGER.warn("OpenAL special effects for sounds will not be available");
        }

    }

    private static boolean doEnhancedSounds() {
        if (!Client.Config.enhancedSounds.enableEnhancedSounds) {
            LOGGER.warn("Enhanced sounds are not enabled.  No fancy sounds for you!");
            return false;
        }

        return true;
    }

    public static void deinitialize(final SoundEngine soundEngine) {
        SoundFXProcessor.deinitialize();
    }

    /**
     * Hook that is called when the sound is actually being queued down into the engine.  Use this to determine
     * what actually got played and to perform logging.  The standard sound listener will not receive callbacks if
     * the sound is too far away (based on the sound instance distance value).
     * @param sound Sound that is being queued into the audio engine
     */
    public static void onPlaySound(final SoundInstance sound) {
        LOGGER.debug(Configuration.Flags.BASIC_SOUND_PLAY, () -> String.format("PLAYING: [%s]", debugString(sound)));
    }
}
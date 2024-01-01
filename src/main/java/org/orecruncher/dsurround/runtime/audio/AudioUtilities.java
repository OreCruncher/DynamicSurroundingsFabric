package org.orecruncher.dsurround.runtime.audio;

import com.mojang.blaze3d.audio.Listener;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.*;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Lazy;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.platform.IPlatform;
import org.orecruncher.dsurround.mixins.core.MixinAbstractSoundInstance;
import org.orecruncher.dsurround.mixins.audio.MixinSoundManagerAccessor;
import org.orecruncher.dsurround.mixins.audio.MixinSoundSystemAccessors;
import org.orecruncher.dsurround.mixinutils.ISoundEngine;

import java.util.function.Supplier;

public final class AudioUtilities {
    private static final IModLog LOGGER = ContainerManager.resolve(IModLog.class);

    // If these mods are present, enhanced sound processing will be disabled.
    private static final ObjectArray<String> autoDisabledBecauseOf = new ObjectArray<>();

    private static final Lazy<Boolean> advancedProcessingEnabled = new Lazy<>(() -> {
        // First check general settings
        var config = ContainerManager.resolve(Configuration.EnhancedSounds.class);
        if (config.enableEnhancedSounds) {
            // Next check to see if any present mods are in our exclusion list
            for (var modId : autoDisabledBecauseOf) {
                var platform = ContainerManager.resolve(IPlatform.class);
                if (platform.isModLoaded(modId)) {
                    LOGGER.warn("Enhanced sound processing is auto disabled due to the presence of the mod \"%s\"", modId);
                    return false;
                }
            }
            return true;
        }

        return false;
    });

    private static int MAX_SOUNDS = 0;

    static {
        autoDisabledBecauseOf.add("sound_physics_remastered");
    }

    public static int getMaxSounds() {
        return MAX_SOUNDS;
    }

    public static SoundEngine getSoundSystem() {
        var soundManager = GameUtils.getSoundManager();
        MixinSoundManagerAccessor manager = (MixinSoundManagerAccessor) soundManager;
        return manager.dsurround_getSoundSystem();
    }

    public static Listener getSoundListener() {
        return ((MixinSoundSystemAccessors)getSoundSystem()).dsurround_getListener();
    }

    /**
     * Reuse the builder to help mitigate heap issues during heavy log volume
     */
    private static final ThreadLocal<StringBuilder> builder = ThreadLocal.withInitial(() -> new StringBuilder(128));

    /**
     * Provides a debug string for the specified sound object.
     *
     * @param sound Sound instance to provide a debug string for
     * @return Debug string
     */
    public static String debugString(final SoundInstance sound) {

        try {
            var sb = builder.get();
            sb.setLength(0);

            MixinAbstractSoundInstance accessor = (MixinAbstractSoundInstance) sound;
            sb.append(sound.getClass().getSimpleName()).append("{");
            sb.append(sound.getLocation());
            sb.append(", ").append(sound.getSource().getName());
            sb.append(", ").append(sound.getAttenuation().toString());
            sb.append(String.format(", (%.2f,%.2f,%.2f)", sound.getX(), sound.getY(), sound.getZ()));

            // Depending on call context the sound property may be null
            sb.append(String.format(", v: %.4f(%.4f)", sound.getVolume(), accessor.dsurround_getRawVolume()));
            sb.append(String.format(", p: %.4f(%.4f)", sound.getPitch(), accessor.dsurround_getRawPitch()));
            sb.append(", s: ").append(sound.getSound().shouldStream());

            sb.append(", g: ").append(sound.isRelative());
            sb.append("}");

            if (!sound.isRelative()) {
                var listener = getSoundListener();
                var distance = Math.sqrt(listener.getTransform().position().distanceToSqr(sound.getX(), sound.getY(), sound.getZ()));
                sb.append(String.format(", distance: %.1f", distance));
                sb.append(" (").append(sound.getSound().getAttenuationDistance()).append(")");
            }

            return sb.toString();
        } catch (Throwable ignore) {
        }

        return "Unable to format sound!";
    }

    /**
     * This method is invoked via the MixinSoundSystem injection.  It will be called when the sound system
     * is initialized, and it gives an opportunity to set up special effects processing.
     *
     * @param soundEngine The sound system instance being initialized
     */
    public static void initialize(final SoundManager soundEngine) {

        try {

            long devicePointer = ((ISoundEngine)soundEngine).dsurround_getDevicePointer();

            // Calculate the number of source slots available
            MAX_SOUNDS = ALC11.alcGetInteger(devicePointer, ALC11.ALC_MONO_SOURCES);

            // Do this last because it is dependent on the sound calculations
            if (doEnhancedSounds())
                SoundFXProcessor.initialize();
            else
                LOGGER.warn("Enhanced sound processing is disabled");

            final String vendor = AL10.alGetString(AL10.AL_VENDOR);
            final String version = AL10.alGetString(AL10.AL_VERSION);
            final String renderer = AL10.alGetString(AL10.AL_RENDERER);
            final String extensions = AL10.alGetString(AL10.AL_EXTENSIONS);

            final int frequency = ALC11.alcGetInteger(devicePointer, ALC11.ALC_FREQUENCY);
            final int auxSendsConfigured = ALC11.alcGetInteger(devicePointer, EXTEfx.ALC_MAX_AUXILIARY_SENDS);

            LOGGER.info("Vendor: %s", vendor);
            LOGGER.info("Version: %s", version);
            LOGGER.info("Renderer: %s", renderer);
            LOGGER.info("Frequency: %d", frequency);
            LOGGER.info("AuxSends: %d", auxSendsConfigured);
            LOGGER.info("Extensions: %s", extensions);

        } catch (final Throwable t) {
            LOGGER.warn(t.getMessage());
            LOGGER.warn("OpenAL special effects for sounds will not be available");
        }
    }

    public static boolean doEnhancedSounds() {
        return advancedProcessingEnabled.get();
    }

    public static void deinitialize(final SoundManager ignore) {
        if (doEnhancedSounds())
            SoundFXProcessor.deinitialize();
    }

    /**
     * Hook that is called when the sound is actually being queued down into the engine.  Use this to determine
     * what actually got played and to perform logging.  The standard sound listener will not receive callbacks if
     * the sound is too far away (based on the sound instance distance value).
     * @param sound Sound that is being queued into the audio engine
     */
    public static void onSoundPlay(final SoundInstance sound) {
        LOGGER.debug(Configuration.Flags.BASIC_SOUND_PLAY, () -> "PLAYING: " + debugString(sound));
    }

    /**
     * Executes the specified @param Runnable checking the AL error status after execution.
     * @param func Runnable to execute against the sound library
     * @param context Context in which the command is being executed
     */
    public static int execute(final Runnable func, @Nullable final Supplier<String> context) {
        func.run();
        final int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            String errorName = AL10.alGetString(error);
            if (StringUtils.isEmpty(errorName))
                errorName = Integer.toString(error);

            String msg = null;
            if (context != null)
                msg = context.get();
            if (msg == null)
                msg = "NONE";

            LOGGER.warn(String.format("OpenAL Error: %s [%s]", errorName, msg));
        }
        return error;
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
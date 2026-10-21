package org.orecruncher.dsurround.sound;

import com.google.common.base.Preconditions;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.gui.sound.ConfigSoundInstance;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;

/**
 * Special hook into the Minecraft SoundSystem.  This logic scales the volume of a sound
 * based on configuration information.  This allows tuning of sound volumes on a per-sound
 * basis.
 * <p>
 * It takes the place of the game's volume for a sound everywhere the game works one out: when it starts, every tick
 * for a ticking sound (the mod's own loops among them), and for every playing sound when a volume slider moves.
 */
public final class SoundVolumeEvaluator {

    private SoundVolumeEvaluator() {
    }

    // Resolved on first use, so the arithmetic can be tested without the container
    private static final class Holder {
        static final ISoundLibrary SOUND_LIBRARY = ContainerManager.resolve(ISoundLibrary.class);
    }

    /**
     * The sound's gain, its volume read from it. Client thread only: reading the volume draws on the sound's random
     * source.
     */
    public static float getAdjustedVolume(final SoundInstance sound, @Nullable SoundSource soundSource) {
        Preconditions.checkNotNull(sound);
        return getAdjustedVolume(sound, sound.getVolume(), soundSource);
    }

    /**
     * The sound's gain for a volume already read from it (the game reads it once in play(), and works out the
     * sound's range from the same value)
     */
    public static float getAdjustedVolume(final SoundInstance sound, float volume, @Nullable SoundSource soundSource) {
        Preconditions.checkNotNull(sound);

        // Scale the volume based on Minecraft's volume scaling selections.
        final SoundSource category = soundSource != null ? soundSource : sound.getSource();
        var categoryFactor = category == SoundSource.MASTER ? 1F : GameUtils.getGameSettings().getSoundSourceVolume(category);

        // Config sounds are played from the config menu: their volume already carries the slider's scale, so the
        // configured one isn't applied again.
        var volumeScale = sound instanceof ConfigSoundInstance ? 1F : Holder.SOUND_LIBRARY.getVolumeScale(category, sound.getLocation());
        return gain(volume, categoryFactor, volumeScale);
    }

    /**
     * The gain for a sound's volume, its category's slider and its configured scale. The game's own gain is the
     * volume times the slider, at most 1: a volume above 1 carries the sound further rather than making it louder
     * (explosions are 4). The configured scale applies to that gain, so turning a loud sound down quietens it; it
     * may raise a quiet one, up to 1. A scale of 1 leaves the game's gain as it is.
     */
    static float gain(float volume, float categoryFactor, float volumeScale) {
        float game = Mth.clamp(volume * categoryFactor, 0F, 1F);
        return Mth.clamp(game * volumeScale, 0F, 1F);
    }
}

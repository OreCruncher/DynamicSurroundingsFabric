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
 * It scales the game's gain for a sound everywhere the game works one out: when it starts, every tick for a ticking
 * sound (the mod's own loops among them), and for every playing sound when a volume slider moves. The game's gain
 * already holds the master and category sliders and a category's fade (the music's), so only the configured scale is
 * applied here.
 */
public final class SoundVolumeEvaluator {

    private SoundVolumeEvaluator() {
    }

    // Resolved on first use, so the arithmetic can be tested without the container
    private static final class Holder {
        static final ISoundLibrary SOUND_LIBRARY = ContainerManager.resolve(ISoundLibrary.class);
    }

    /**
     * The sound's gain, for diagnostics: the game's worked out as SoundEngine.calculateVolume does, without a fade in
     * progress, then scaled. Client thread only: reading the volume draws on the sound's random source.
     */
    public static float getAdjustedVolume(final SoundInstance sound, @Nullable SoundSource soundSource) {
        Preconditions.checkNotNull(sound);
        final SoundSource category = soundSource != null ? soundSource : sound.getSource();
        float sliders = Mth.clamp(GameUtils.getGameSettings().getFinalSoundSourceVolume(category), 0F, 1F);
        return scaleGain(sound, Mth.clamp(sound.getVolume(), 0F, 1F) * sliders, soundSource);
    }

    /**
     * The game's gain for a sound with its configured scale applied
     */
    public static float scaleGain(final SoundInstance sound, float gameGain, @Nullable SoundSource soundSource) {
        Preconditions.checkNotNull(sound);
        final SoundSource category = soundSource != null ? soundSource : sound.getSource();
        // Config sounds are played from the config menu: their volume already carries the slider's scale, so the
        // configured one isn't applied again.
        var volumeScale = sound instanceof ConfigSoundInstance ? 1F : Holder.SOUND_LIBRARY.getVolumeScale(category, sound.getIdentifier());
        return gain(gameGain, volumeScale);
    }

    /**
     * The gain for the game's gain and a configured scale. The game's gain is at most 1: a volume above 1 carries the
     * sound further rather than making it louder (explosions are 4). The configured scale applies to that gain, so
     * turning a loud sound down quietens it; it may raise a quiet one, up to 1. A scale of 1 leaves the game's gain as
     * it is.
     */
    static float gain(float gameGain, float volumeScale) {
        return Mth.clamp(gameGain * volumeScale, 0F, 1F);
    }
}

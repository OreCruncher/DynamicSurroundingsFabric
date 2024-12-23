package org.orecruncher.dsurround.sound;

import com.google.common.base.Preconditions;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.gui.sound.ConfigSoundInstance;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;

/**
 * Special hook into the Minecraft SoundSystem.  This logic scales the volume of a sound
 * based on configuration information.  This allows tuning of sound volumes on a per-sound
 * basis.
 */
public final class SoundVolumeEvaluator {

    private static final ISoundLibrary SOUND_LIBRARY = ContainerManager.resolve(ISoundLibrary.class);

    /**
     * This guy is hooked by a Mixin to replace getClampedVolume() in Minecraft code.
     */
    public static float getAdjustedVolume(final SoundInstance sound) {
        Preconditions.checkNotNull(sound);

        float volume = sound.getVolume();

        // Scale the volume based on Minecraft's volume scaling selections.
        final SoundSource category = sound.getSource();
        var categoryFactor =  category == SoundSource.MASTER ? 1F : GameUtils.getGameSettings().getSoundSourceVolume(category);
        volume *= categoryFactor;

        // Config sounds are played from the config menu.  Do not scale volume
        // with category adjustments.
        if (!(sound instanceof ConfigSoundInstance)) {
            // Further scale based on the sound's configuration within the mod data set. It's possible that this
            // could result in a sound volume of 0.
            var volumeScale = SOUND_LIBRARY.getVolumeScale(category, sound.getLocation());
            volume *= volumeScale;
        }

        return Mth.clamp(volume, 0, 1F);
    }
}
package org.orecruncher.dsurround.sound;

import com.google.common.base.Preconditions;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.gui.sound.ConfigSoundInstance;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;

/**
 * Special hook into the Minecraft SoundSystem.  This logic scales the volume of a sound
 * based on configuration information.  This allows tuning of sound volumes on a per
 * sound basis.
 */
public final class SoundVolumeEvaluator {

    private static final ISoundLibrary soundLibrary = ContainerManager.resolve(ISoundLibrary.class);

    private static float getCategoryVolumeScale(final SoundInstance sound) {
        // Master category already controlled by master gain so ignore
        final SoundCategory category = sound.getCategory();
        return category == SoundCategory.MASTER ? 1F : GameUtils.getGameSettings().getSoundVolume(category);
    }

    /**
     * This guy is hooked by a Mixin to replace getClampedVolume() in Minecraft code.
     */
    public static float getAdjustedVolume(final SoundInstance sound) {
        Preconditions.checkNotNull(sound);

        float volume = sound.getVolume();

        // Config sounds are played from the config menu.  Do not scale volume
        // with category adjustments.
        if (!(sound instanceof ConfigSoundInstance)) {
            volume *= getCategoryVolumeScale(sound);

            var volumeScale = soundLibrary.getVolumeScale(sound.getId());
            if (volumeScale != 0f) {
                volume *= volumeScale;
            }
        }

        return MathHelper.clamp(volume, 0, 1F);
    }
}
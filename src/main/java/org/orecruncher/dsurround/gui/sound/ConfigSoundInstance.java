package org.orecruncher.dsurround.gui.sound;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Special sound instance created by the sound configuration option menu.  The type is detected through the pipeline
 * to avoid applying behaviors like blocking and volume scaling.
 */
public class ConfigSoundInstance extends SimpleSoundInstance {
    public ConfigSoundInstance(ResourceLocation id, int volumeScale) {
        super(id, SoundSource.AMBIENT, volumeScale / 100F, 1F, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true);
    }
}

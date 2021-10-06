package org.orecruncher.dsurround.config.adapters;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.BiomeMoodSound;
import net.minecraft.sound.SoundEvent;

@Environment(EnvType.CLIENT)
public class BiomeMoodSoundAdaptor extends BiomeMoodSound {
    public BiomeMoodSoundAdaptor(SoundEvent soundEvent, int cultivationTicks, int spawnRange, double extraDistance) {
        super(soundEvent, cultivationTicks, spawnRange, extraDistance);
    }
}

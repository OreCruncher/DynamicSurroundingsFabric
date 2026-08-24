package org.orecruncher.dsurround.mixinutils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;

public interface IBiomeExtended {

    float dsurround_getTemperature(BlockPos pos);

    Biome.ClimateSettings dsurround_getWeather();

    BiomeSpecialEffects dsurround_getSpecialEffects();
}

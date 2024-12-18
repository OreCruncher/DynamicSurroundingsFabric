package org.orecruncher.dsurround.mixinutils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.orecruncher.dsurround.config.biome.BiomeInfo;

public interface IBiomeExtended {

    BiomeInfo dsurround_getInfo();

    void dsurround_setInfo(BiomeInfo info);

    float dsurround_getTemperature(BlockPos pos);

    Biome.ClimateSettings dsurround_getWeather();

    BiomeSpecialEffects dsurround_getSpecialEffects();

}

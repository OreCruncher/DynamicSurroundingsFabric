package org.orecruncher.dsurround.mixinutils;

import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;

public interface IBiomeExtended {

    BiomeInfo dsurround_getInfo();

    void dsurround_setInfo(BiomeInfo info);

    Biome.ClimateSettings dsurround_getWeather();
}

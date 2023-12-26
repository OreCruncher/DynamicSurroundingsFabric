package org.orecruncher.dsurround.xface;

import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;

public interface IBiomeExtended {

    BiomeInfo dsurround_getInfo();

    void dsurround_setInfo(BiomeInfo info);

    Biome.Weather dsurround_getWeather();
}

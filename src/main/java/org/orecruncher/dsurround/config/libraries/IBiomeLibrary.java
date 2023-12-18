package org.orecruncher.dsurround.config.libraries;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.config.InternalBiomes;
import org.orecruncher.dsurround.config.biome.BiomeInfo;

public interface IBiomeLibrary extends ILibrary {
    BiomeInfo getBiomeInfo(Biome biome);
    BiomeInfo getBiomeInfo(InternalBiomes biome);
    String getBiomeName(Identifier id);
}

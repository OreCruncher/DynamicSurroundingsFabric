package org.orecruncher.dsurround.config.libraries;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.SyntheticBiome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.lib.scripting.Script;

public interface IBiomeLibrary extends ILibrary {
    BiomeInfo getBiomeInfo(Biome biome);
    BiomeInfo getBiomeInfo(SyntheticBiome biome);
    String getBiomeName(ResourceLocation id);

    /**
     * Adhoc execution of a script vs the specified biome.  Used by the dsbiome command.
     * Not to be used for other purposes.
     */
    Object eval(Biome biome, Script script);
}

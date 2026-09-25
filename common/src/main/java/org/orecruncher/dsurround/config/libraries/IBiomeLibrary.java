package org.orecruncher.dsurround.config.libraries;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.SyntheticBiome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.lib.scripting.Script;

public interface IBiomeLibrary extends ILibrary {
    /**
     * Used to obtain a BiomeInfo object if one has already been created. Used by mixins to cover the case
     * of when a biome is dynamically modified via code during client initialization. Should only be called
     * if necessary.
     */
    @Nullable BiomeInfo getBiomeInfoWeak(Biome biome);
    BiomeInfo getBiomeInfo(Biome biome);
    BiomeInfo getBiomeInfo(SyntheticBiome biome);
    String getBiomeName(Identifier id);

    /**
     * Adhoc execution of a script vs the specified biome.  Used by the dsbiome command.
     * Not to be used for other purposes.
     */
    Object eval(Biome biome, Script script);
}

package org.orecruncher.dsurround.config.libraries;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.SyntheticBiome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.lib.scripting.Script;

/**
 * Per-biome settings (traits, fog, sounds) from biomes.json. Client thread only.
 */
public interface IBiomeLibrary extends ILibrary {
    /**
     * The biome's info if it has already been built, otherwise null; never builds anything. Used by mixins, which
     * can run before the library is ready (or while a biome is being modified during client initialization), and
     * on the fog path, which runs hundreds of times per frame.
     */
    @Nullable BiomeInfo findBiomeInfo(Biome biome);

    /**
     * The biome's info, building and caching it on first request.
     */
    BiomeInfo getBiomeInfo(Biome biome);
    BiomeInfo getBiomeInfo(SyntheticBiome biome);
    String getBiomeName(ResourceLocation id);

    /**
     * Adhoc execution of a script vs the specified biome.  Used by the dsbiome command.
     * Not to be used for other purposes.
     */
    Object eval(Biome biome, Script script);
}

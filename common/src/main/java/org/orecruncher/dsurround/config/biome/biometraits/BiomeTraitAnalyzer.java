package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.util.Set;

public final class BiomeTraitAnalyzer  implements IBiomeTraitAnalyzer {

    @Override
    public void analyze(@NotNull ResourceLocation id, @NotNull Biome biome, @NotNull Set<BiomeTrait> resultCollection) {
        // Add additional tags for completeness
        if (resultCollection.contains(BiomeTrait.CAVE))
            resultCollection.add(BiomeTrait.UNDERGROUND);
    }
}

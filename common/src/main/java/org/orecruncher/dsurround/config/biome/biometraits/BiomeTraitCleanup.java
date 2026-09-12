package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.BiomeTrait;
import static org.orecruncher.dsurround.config.BiomeTrait.*;

import java.util.Set;

/**
 * Cleans up a set of biome traits, removing conflicting traits (such as NETHER and OVERWORLD in the same set)
 */
public final class BiomeTraitCleanup implements IBiomeTraitAnalyzer {
    @Override
    public String name() {
        return "BiomeTraitCleanup";
    }

    @Override
    public void analyze(@NotNull ResourceLocation id, @NotNull Biome biome, @NotNull Set<BiomeTrait> resultCollection) {
        if (resultCollection.contains(NETHER)) {
            resultCollection.removeAll(Set.of(OVERWORLD, END));
        }

        if (resultCollection.contains(END)) {
            resultCollection.remove(OVERWORLD);
        }

        if (resultCollection.contains(DENSE_VEGETATION)) {
            resultCollection.remove(SPARSE_VEGETATION);
        }

        if (resultCollection.contains(WET)) {
            resultCollection.remove(DRY);
        }

        if (resultCollection.contains(TEMPERATE)) {
            resultCollection.removeAll(Set.of(COLD, HOT));
        }

        if (resultCollection.contains(HOT)) {
            resultCollection.remove(COLD);
        }

        if (resultCollection.contains(RIVER)) {
            resultCollection.removeAll(Set.of(OCEAN, DEEP_OCEAN, SHALLOW_OCEAN));
        }

        if (resultCollection.contains(DEEP_OCEAN)) {
            resultCollection.removeAll(Set.of(OCEAN, SHALLOW_OCEAN));
        }

        if (resultCollection.contains(SHALLOW_OCEAN)) {
            resultCollection.remove(OCEAN);
        }
    }
}

package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.BiomeTrait;
import static org.orecruncher.dsurround.config.BiomeTrait.*;

import java.util.Collections;
import java.util.Set;

/**
 * Generates biome traits for a biome based on other traits that have been collected thus far.
 */
public final class BiomeTraitAnalyzer implements IBiomeTraitAnalyzer {

    @Override
    public String name() {
        return "BiomeTraitAnalyzer";
    }

    @Override
    public void analyze(@NotNull ResourceLocation id, @NotNull Biome biome, @NotNull Set<BiomeTrait> resultCollection) {
        if (resultCollection.contains(CAVE))
            resultCollection.add(UNDERGROUND);

        if (!Collections.disjoint(resultCollection, Set.of(SNOWY_PLAINS, SNOWY, ICY)))
            resultCollection.add(COLD);

        if (!Collections.disjoint(resultCollection, Set.of(OCEAN, DEEP_OCEAN, SHALLOW_OCEAN, RIVER)))
            resultCollection.add(AQUATIC);

        if (resultCollection.containsAll(Set.of(AQUATIC, ICY)))
            resultCollection.add(AQUATIC_ICY);
    }
}

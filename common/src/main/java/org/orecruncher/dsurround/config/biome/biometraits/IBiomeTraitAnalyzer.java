package org.orecruncher.dsurround.config.biome.biometraits;

import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.util.Set;

/**
 * One way of working out a biome's traits. Each is given the traits found so far, and adds to them or (cleanup)
 * takes some away; {@link BiomeTraits} notes where each came from.
 */
public interface IBiomeTraitAnalyzer {
    String name();

    void analyze(@NotNull BiomeEvidence biome, @NotNull Set<BiomeTrait> resultCollection);
}

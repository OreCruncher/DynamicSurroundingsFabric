package org.orecruncher.dsurround.config.biome.biometraits;

import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.util.Set;

import static org.orecruncher.dsurround.config.BiomeTrait.*;

/**
 * Traits from a biome's climate, the numbers its author gave it: cold, temperate or hot by its temperature; dry if it
 * never rains (as in deserts, savannas, badlands, the Nether and the End), otherwise dry or wet by its downfall; icy
 * if its water freezes over (vanilla's frozen oceans).
 */
public final class BiomeClimateAnalyzer implements IBiomeTraitAnalyzer {

    @Override
    public String name() {
        return "BiomeClimateAnalyzer";
    }

    @Override
    public void analyze(@NotNull BiomeEvidence biome, @NotNull Set<BiomeTrait> resultCollection) {
        addClimateTraits(biome.hasPrecipitation(), biome.temperature(), biome.frozen(), biome.downfall(), resultCollection);
    }

    static void addClimateTraits(boolean hasPrecipitation, float temperature, boolean frozen, float downfall, Set<BiomeTrait> results) {
        if (temperature <= 0.15F) {
            results.add(COLD);
        } else if (temperature >= 0.95F) {
            results.add(HOT);
        } else {
            results.add(TEMPERATE);
        }

        if (!hasPrecipitation || downfall <= 0.15F) {
            results.add(DRY);
        } else if (downfall >= 0.70F) {
            results.add(WET);
        }

        if (frozen)
            results.add(ICY);
    }
}

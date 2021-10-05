package org.orecruncher.dsurround.config.biometraits;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

public class BiomeRainfallAnalyzer implements IBiomeTraitAnalyzer {

    private static final String ARID = "arid";
    private static final String WET = "wet";

    @Override
    public @Nullable String evaluate(Identifier id, Biome biome) {
        float rainfall = biome.getDownfall();

        if (rainfall < 0.15F)
            return ARID;

        if (rainfall > 0.85F)
            return WET;

        return null;
    }
}

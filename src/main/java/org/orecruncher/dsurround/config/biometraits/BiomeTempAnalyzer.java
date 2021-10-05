package org.orecruncher.dsurround.config.biometraits;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

public class BiomeTempAnalyzer implements IBiomeTraitAnalyzer {

    private static final String FROZEN = "frozen";
    private static final String COLD = "cold";
    private static final String HELL = "hell";
    private static final String HOT = "hot";

    @Override
    public @Nullable String evaluate(Identifier id, Biome biome) {
        float biomeTemp = biome.getTemperature();

        if (biomeTemp < 0F)
            return FROZEN;

        if (biomeTemp < 0.15F)
            return COLD;

        if (biomeTemp > 2F)
            return HELL;

        if (biomeTemp > 1F)
            return HOT;

        return null;
    }
}

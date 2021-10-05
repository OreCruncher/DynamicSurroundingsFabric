package org.orecruncher.dsurround.config.biometraits;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

public class BiomePrecipitationAnalyzer implements IBiomeTraitAnalyzer {

    private static final String RAIN = "rain";
    private static final String SNOW = "snow";

    @Override
    public @Nullable String evaluate(Identifier id, Biome biome) {
        return switch (biome.getPrecipitation()) {
            case RAIN -> RAIN;
            case SNOW -> SNOW;
            default -> null;
        };
    }
}

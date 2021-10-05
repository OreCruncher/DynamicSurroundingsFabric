package org.orecruncher.dsurround.config.biometraits;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

public class BiomeExtremeAnalyzer implements IBiomeTraitAnalyzer {

    private static final String EXTREME = "extreme";

    @Override
    public @Nullable String evaluate(Identifier id, Biome biome) {
        return id.getPath().contains(EXTREME) ? EXTREME : null;
    }
}

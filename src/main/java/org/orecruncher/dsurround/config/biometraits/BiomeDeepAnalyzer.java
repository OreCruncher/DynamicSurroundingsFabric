package org.orecruncher.dsurround.config.biometraits;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

public class BiomeDeepAnalyzer implements IBiomeTraitAnalyzer {

    private static final String DEEP = "deep";

    @Override
    public @Nullable String evaluate(Identifier id, Biome biome) {
        return biome.getDepth() < -1.7 ? DEEP : null;
    }
}

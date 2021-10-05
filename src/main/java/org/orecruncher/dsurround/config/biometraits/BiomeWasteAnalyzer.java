package org.orecruncher.dsurround.config.biometraits;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

public class BiomeWasteAnalyzer implements IBiomeTraitAnalyzer {

    private static final String WASTE = "waste";

    @Override
    public @Nullable String evaluate(Identifier id, Biome biome) {
        return id.getPath().contains(WASTE) ? WASTE : null;
    }
}

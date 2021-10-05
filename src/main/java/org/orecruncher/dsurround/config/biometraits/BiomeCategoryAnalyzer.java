package org.orecruncher.dsurround.config.biometraits;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

public class BiomeCategoryAnalyzer implements IBiomeTraitAnalyzer {
    @Override
    public @Nullable String evaluate(Identifier id, Biome biome) {
        return biome.getCategory().getName();
    }
}

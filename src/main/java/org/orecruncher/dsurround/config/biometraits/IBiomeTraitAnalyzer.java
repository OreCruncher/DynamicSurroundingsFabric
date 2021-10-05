package org.orecruncher.dsurround.config.biometraits;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

public interface IBiomeTraitAnalyzer {
    @Nullable
    String evaluate(Identifier id, Biome biome);
}

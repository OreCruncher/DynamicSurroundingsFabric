package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.Collection;

public interface IBiomeTraitAnalyzer {
    Collection<BiomeTrait> evaluate(ResourceLocation id, Biome biome, Holder<Biome> biomeEntry);
}

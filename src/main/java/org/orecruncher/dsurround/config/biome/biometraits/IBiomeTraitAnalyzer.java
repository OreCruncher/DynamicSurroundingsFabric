package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.util.Collection;

public interface IBiomeTraitAnalyzer {
    Collection<BiomeTrait> evaluate(ResourceLocation id, Biome biome);
}

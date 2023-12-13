package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.Collection;

public interface IBiomeTraitAnalyzer {
    Collection<BiomeTrait> evaluate(Identifier id, Biome biome, RegistryEntry.Reference<Biome> biomeEntry);
}

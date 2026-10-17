package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.util.Set;

public interface IBiomeTraitAnalyzer {
    String name();
    void analyze(@NotNull Identifier id, @NotNull Biome biome, @NotNull Set<BiomeTrait> resultCollection);
}

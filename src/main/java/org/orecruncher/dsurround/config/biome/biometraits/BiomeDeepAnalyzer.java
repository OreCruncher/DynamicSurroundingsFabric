package org.orecruncher.dsurround.config.biome.biometraits;

import com.google.common.collect.ImmutableList;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.Collection;

public class BiomeDeepAnalyzer implements IBiomeTraitAnalyzer {

    private static final Collection<BiomeTrait> result = ImmutableList.of(BiomeTrait.DEEP);

    @Override
    public Collection<BiomeTrait> evaluate(Identifier id, Biome biome) {
        return ImmutableList.of();
    }
}

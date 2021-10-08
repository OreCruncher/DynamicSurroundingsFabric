package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BiomeCategoryAnalyzer implements IBiomeTraitAnalyzer {
    @Override
    public Collection<BiomeTrait> evaluate(Identifier id, Biome biome) {
        List<BiomeTrait> results = new ArrayList<>();

        var category = biome.getCategory();
        results.add(BiomeTrait.of(category));

        if (category == Biome.Category.OCEAN || category == Biome.Category.RIVER)
            results.add(BiomeTrait.WATER);

        if (category == Biome.Category.BEACH || category == Biome.Category.DESERT)
            results.add(BiomeTrait.SANDY);

        if (category != Biome.Category.NETHER && category != Biome.Category.THEEND)
            results.add(BiomeTrait.OVERWORLD);

        if (category == Biome.Category.TAIGA)
            results.add(BiomeTrait.CONIFEROUS);

        return results;
    }
}

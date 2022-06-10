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

        var path = id.getPath();
//        var category = ((BiomeAccessor) (Object) biome).getCategory();
//        results.add(BiomeTrait.of(category));

        if (path.contains("ocean") || path.contains("river"))
//        if (category == Biome.Category.OCEAN || category == Biome.Category.RIVER)
            results.add(BiomeTrait.WATER);

        if (path.contains("beach") || path.contains("desert") || path.contains("badlands"))
//        if (category == Biome.Category.BEACH || category == Biome.Category.DESERT)
            results.add(BiomeTrait.SANDY);

        if (!path.contains("nether") && !path.contains("soul_sand_valley") && !path.contains("basalt_deltas") && !path.contains("warped_forest") && !path.contains("crimson_forest") && !path.contains("the_end") && !path.contains("end_"))
//        if (category != Biome.Category.NETHER && category != Biome.Category.THEEND)
            results.add(BiomeTrait.OVERWORLD);

        if (path.contains("taiga"))
//        if (category == Biome.Category.TAIGA)
            results.add(BiomeTrait.CONIFEROUS);

        return results;
    }
}

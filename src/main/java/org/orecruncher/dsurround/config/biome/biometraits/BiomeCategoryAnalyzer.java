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

        // dimension traits
        if (path.contains("nether") || path.contains("soul_sand_valley") || path.contains("basalt_deltas") || path.contains("warped_forest") || path.contains("crimson_forest"))
            results.add(BiomeTrait.NETHER);
        else if (path.contains("the_end") || path.contains("end_"))
            results.add(BiomeTrait.THEEND);
        else
            results.add(BiomeTrait.OVERWORLD);

        // additional special traits
        if (path.contains("ocean") || path.contains("river"))
            results.add(BiomeTrait.WATER);
        if (path.contains("beach") || path.contains("desert") || path.contains("badlands"))
            results.add(BiomeTrait.SANDY);
        if (path.contains("taiga"))
            results.add(BiomeTrait.CONIFEROUS);


        // normal biome traits by category (VERY UGLY FIX ME)
        if (path.contains("windswept"))
            results.add(BiomeTrait.EXTREME_HILLS);
        if (path.contains("badlands"))
            results.add(BiomeTrait.MESA);
        if (path.contains("slopes") || path.contains("peaks"))
            results.add(BiomeTrait.MOUNTAIN);
        if (path.contains("caves"))
            results.add(BiomeTrait.UNDERGROUND);
        if (path.contains("meadow"))
            results.add(BiomeTrait.MOUNTAIN);
        if (path.contains("plains"))
            results.add(BiomeTrait.PLAINS);
        if (path.contains("forest"))
            results.add(BiomeTrait.FOREST);
        if (path.contains("swamp"))
            results.add(BiomeTrait.SWAMP);
        if (path.contains("desert"))
            results.add(BiomeTrait.DESERT);
        if (path.contains("taiga"))
            results.add(BiomeTrait.TAIGA);
        if (path.contains("savanna"))
            results.add(BiomeTrait.SAVANNA);
        if (path.contains("jungle"))
            results.add(BiomeTrait.JUNGLE);
        if (path.contains("grove"))
            results.add(BiomeTrait.FOREST);
        if (path.contains("river"))
            results.add(BiomeTrait.RIVER);
        if (path.contains("beach"))
            results.add(BiomeTrait.BEACH);
        if (path.contains("ocean"))
            results.add(BiomeTrait.OCEAN);
        if (path.contains("mushroom"))
            results.add(BiomeTrait.MUSHROOM);


        return results;
    }
}

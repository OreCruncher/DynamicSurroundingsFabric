package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.mixins.core.BiomeAccessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BiomeClimateAnalyzer implements IBiomeTraitAnalyzer {

    @Override
    public Collection<BiomeTrait> evaluate(Identifier id, Biome biome) {
        List<BiomeTrait> results = new ArrayList<>();

        var path = id.getPath();

        // If it's The End there isn't much going on.
        if (path.contains("the_end") || path.contains("end_")) {
//        if (category == Biome.Category.THEEND) {
            results.add(BiomeTrait.VOID);
            return results;
        }

        if (biome.getPrecipitation() == Biome.Precipitation.SNOW)
            results.add(BiomeTrait.SNOWY);

        var biomeTemp = biome.getTemperature();

        // Nether is always hot
        if (path.contains("nether") || path.contains("soul_sand_valley") || path.contains("basalt_deltas") || path.contains("warped_forest") || path.contains("crimson_forest"))
//        if (category == Biome.Category.NETHER)
            results.add(BiomeTrait.HOT);
        else if (biomeTemp < 0.15F)
            results.add(BiomeTrait.COLD);
        else if (biomeTemp > 1F)
            results.add(BiomeTrait.HOT);

        var rainfall = biome.getDownfall();

        if (path.contains("jungle") || path.contains("swamp"))
//        if (category == Biome.Category.JUNGLE || category == Biome.Category.SWAMP)
            results.add(BiomeTrait.WET);
        else if (rainfall < 0.15F)
            results.add(BiomeTrait.DRY);
        else if (rainfall > 0.85)
            results.add(BiomeTrait.WET);

        return results;
    }
}

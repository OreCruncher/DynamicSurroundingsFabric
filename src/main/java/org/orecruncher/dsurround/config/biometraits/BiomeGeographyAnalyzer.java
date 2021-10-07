package org.orecruncher.dsurround.config.biometraits;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BiomeGeographyAnalyzer implements IBiomeTraitAnalyzer {

    @Override
    public Collection<BiomeTrait> evaluate(Identifier id, Biome biome) {
        List<BiomeTrait> results = new ArrayList<>();

        var path = id.getPath();

        if (path.contains("hill"))
            results.add(BiomeTrait.HILLS);

        if (path.contains("mountain"))
            results.add(BiomeTrait.MOUNTAIN);

        if (path.contains("plateau"))
            results.add(BiomeTrait.PLATEAU);

        if (path.contains("giant") || path.contains("tall") || path.contains("dark"))
            results.add(BiomeTrait.DENSE);

        if (path.contains("dead"))
            results.add(BiomeTrait.DEAD);

        if (path.contains("wasteland"))
            results.add(BiomeTrait.WASTELAND);

        if (path.contains("gravelly") || path.contains("wooded"))
            results.add(BiomeTrait.SPARSE);

        // Curious as to the why's.
        if (path.equals("stone_shore"))
            results.add(BiomeTrait.BEACH);

        return results;
    }
}

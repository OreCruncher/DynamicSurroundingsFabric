package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BiomeMysticalAnalyzer implements IBiomeTraitAnalyzer {

    @Override
    public Collection<BiomeTrait> evaluate(ResourceLocation id, Biome biome, Holder<Biome> biomeEntry) {
        List<BiomeTrait> results = new ArrayList<>();

        var path = id.getPath();

        if (path.contains("dark") || path.contains("ominous"))
            results.add(BiomeTrait.SPOOKY);

        if (path.contains("magic") || path.contains("magik"))
            results.add(BiomeTrait.MAGICAL);

        return results;
    }
}

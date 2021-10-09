package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.util.*;
import java.util.stream.Collectors;

public final class BiomeTraits {

    private static final ObjectArray<IBiomeTraitAnalyzer> traitAnalyzer = new ObjectArray<>(16);

    static {
        traitAnalyzer.add(new BiomeCategoryAnalyzer());
        traitAnalyzer.add(new BiomeClimateAnalyzer());
        traitAnalyzer.add(new BiomeDeepAnalyzer());
        traitAnalyzer.add(new BiomeGeographyAnalyzer());
        traitAnalyzer.add(new BiomeMysticalAnalyzer());
    }

    private final Set<BiomeTrait> traits;

    BiomeTraits(Collection<BiomeTrait> traits) {
        this.traits = new HashSet<>(traits);
    }

    public boolean contains(String trait) {
        return contains(BiomeTrait.of(trait));
    }

    public boolean contains(BiomeTrait trait) {
        return this.traits.contains(trait);
    }

    public String toString() {
        var temp = this.traits
                .stream()
                .map(BiomeTrait::getName)
                .collect(Collectors.joining(","));

        return String.format("Traits [%s]", temp);
    }

    public static BiomeTraits createFrom(Identifier id, Biome biome) {
        var traits = traitAnalyzer
                .stream()
                .map(analyzer -> analyzer.evaluate(id, biome))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        return new BiomeTraits(traits);
    }

    public static BiomeTraits from(BiomeTrait... traits) {
        return new BiomeTraits(List.of(traits));
    }
}

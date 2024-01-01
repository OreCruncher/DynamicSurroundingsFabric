package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class BiomeTraits {

    private static final ObjectArray<IBiomeTraitAnalyzer> traitAnalyzer = new ObjectArray<>(16);

    static {
        traitAnalyzer.add(new BiomeTagAnalyzer());
        traitAnalyzer.add(new BiomeMysticalAnalyzer());
    }

    private final Set<BiomeTrait> traits;

    BiomeTraits(Collection<BiomeTrait> traits) {
        this.traits = new HashSet<>(traits);
    }

    public static BiomeTraits createFrom(ResourceLocation id, Biome biome) {
        var biomeEntry = getBiomeEntry(biome);
        var traits = traitAnalyzer
                .stream()
                .map(analyzer -> analyzer.evaluate(id, biome, biomeEntry))
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());
        return new BiomeTraits(traits);
    }

    private static Holder<Biome> getBiomeEntry(Biome biome) {
        return GameUtils.getRegistryEntry(Registries.BIOME, biome).orElseThrow();
    }

    public static BiomeTraits from(BiomeTrait... traits) {
        return new BiomeTraits(List.of(traits));
    }

    public boolean contains(String trait) {
        return contains(BiomeTrait.of(trait));
    }

    public boolean contains(BiomeTrait trait) {
        return this.traits.contains(trait);
    }

    public void forEach(Consumer<BiomeTrait> consumer) {
        for (var t : this.traits)
            consumer.accept(t);
    }

    public String toString() {
        var temp = this.traits
                .stream()
                .map(BiomeTrait::getName)
                .collect(Collectors.joining(", "));

        return String.format("[%s]", temp);
    }
}

package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    public static BiomeTraits createFrom(Identifier id, Biome biome) {
        var biomeEntry = getBiomeEntry(biome);
        var traits = traitAnalyzer
                .stream()
                .map(analyzer -> analyzer.evaluate(id, biome, biomeEntry))
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());
        return new BiomeTraits(traits);
    }

    private static RegistryEntry.Reference<Biome> getBiomeEntry(Biome biome) {
        var manager = GameUtils.getRegistryManager().orElseThrow();
        var biomeRegistry = manager.get(RegistryKeys.BIOME);
        RegistryKey<Biome> key = biomeRegistry.getKey(biome).orElse(BiomeKeys.THE_VOID);
        return biomeRegistry.entryOf(key);
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

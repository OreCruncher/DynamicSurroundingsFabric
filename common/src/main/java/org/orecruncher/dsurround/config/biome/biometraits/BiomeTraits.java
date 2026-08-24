package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class BiomeTraits {

    private static final ObjectArray<IBiomeTraitAnalyzer> traitAnalyzer = new ObjectArray<>(4);

    static {
        traitAnalyzer.add(new BiomeTagAnalyzer());
        traitAnalyzer.add(new BiomeNameFallbackAnalyzer());
        traitAnalyzer.add(new BiomeTraitAnalyzer());
    }

    private final Set<BiomeTrait> traits;
    private boolean updatedByMerge;

    BiomeTraits(Set<BiomeTrait> set) {
        this.traits = set;
    }

    public static BiomeTraits from(ResourceLocation id, Biome biome) {
        EnumSet<BiomeTrait> traits = EnumSet.noneOf(BiomeTrait.class);
        for (var analyzer : traitAnalyzer)
            analyzer.analyze(id, biome, traits);
        return new BiomeTraits(traits);
    }

    public static BiomeTraits of(BiomeTrait... traits) {
        return of(Arrays.asList(traits));
    }

    public static BiomeTraits of(Collection<BiomeTrait> traits) {
        return new BiomeTraits(EnumSet.copyOf(traits));
    }

    public void clear() {
        this.traits.clear();
    }

    public void merge(Collection<BiomeTrait> traits) {
        int count = this.traits.size();
        this.traits.addAll(traits);
        this.updatedByMerge = this.updatedByMerge || count != this.traits.size();
    }

    public boolean contains(String trait) {
        return this.traits.contains(BiomeTrait.of(trait));
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

        var fmt = this.updatedByMerge ? "*[%s]" : "[%s]";
        return fmt.formatted(temp);
    }
}

package org.orecruncher.dsurround.config.biome.biometraits;

import com.google.common.collect.ImmutableList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class BiomeTraits {

    private static final IModLog LOGGER = ContainerManager.memoize(IModLog.class);
    private static final List<IBiomeTraitAnalyzer> TRAIT_ANALYZERS = ImmutableList.of(
            new BiomeTagAnalyzer(),
            new BiomeNameFallbackAnalyzer(),
            new BiomeTraitAnalyzer(),
            // This one should run last
            new BiomeTraitCleanup()
    );

    private final Set<BiomeTrait> traits;
    private boolean updatedByMerge;

    BiomeTraits(Set<BiomeTrait> set) {
        this.traits = set;
    }

    public static BiomeTraits from(Identifier id, Biome biome) {
        EnumSet<BiomeTrait> traits = EnumSet.noneOf(BiomeTrait.class);
        for (var analyzer : TRAIT_ANALYZERS) {
            int before = traits.size();
            analyzer.analyze(id, biome, traits);
            int after = traits.size();
            LOGGER.debug("[%s] %s: %d traits (%d delta)", analyzer.name(), id, after, after - before);
        }
        return new BiomeTraits(traits);
    }

    public static BiomeTraits of(BiomeTrait... traits) {
        var enumSet = EnumSet.noneOf(BiomeTrait.class);
        Collections.addAll(enumSet, traits);
        return new BiomeTraits(enumSet);
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
        return this.contains(BiomeTrait.of(trait));
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

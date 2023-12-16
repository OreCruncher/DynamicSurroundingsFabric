package org.orecruncher.dsurround.config.biome.biometraits;

import net.fabricmc.fabric.api.tag.convention.v1.ConventionalBiomeTags;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.tags.TagHelpers;

import java.util.*;

public class BiomeTagAnalyzer implements IBiomeTraitAnalyzer {

    private static final Map<TagKey<Biome>, BiomeTrait> tagToTraitMap = new HashMap<>();

    static {
        tagToTraitMap.put(ConventionalBiomeTags.IN_OVERWORLD, BiomeTrait.OVERWORLD);
        tagToTraitMap.put(ConventionalBiomeTags.IN_THE_END, BiomeTrait.THEEND);
        tagToTraitMap.put(ConventionalBiomeTags.IN_NETHER, BiomeTrait.NETHER);
        tagToTraitMap.put(ConventionalBiomeTags.TAIGA, BiomeTrait.TAIGA);
        tagToTraitMap.put(ConventionalBiomeTags.EXTREME_HILLS, BiomeTrait.EXTREME_HILLS);
        tagToTraitMap.put(ConventionalBiomeTags.WINDSWEPT, BiomeTrait.WINDSWEPT);
        tagToTraitMap.put(ConventionalBiomeTags.JUNGLE, BiomeTrait.JUNGLE);
        tagToTraitMap.put(ConventionalBiomeTags.MESA, BiomeTrait.MESA);
        tagToTraitMap.put(ConventionalBiomeTags.PLAINS, BiomeTrait.PLAINS);
        tagToTraitMap.put(ConventionalBiomeTags.SAVANNA, BiomeTrait.SAVANNA);
        tagToTraitMap.put(ConventionalBiomeTags.ICY, BiomeTrait.ICY);
        tagToTraitMap.put(ConventionalBiomeTags.BEACH, BiomeTrait.BEACH);
        tagToTraitMap.put(ConventionalBiomeTags.FOREST, BiomeTrait.FOREST);
        tagToTraitMap.put(ConventionalBiomeTags.BIRCH_FOREST, BiomeTrait.FOREST);
        tagToTraitMap.put(ConventionalBiomeTags.OCEAN, BiomeTrait.OCEAN);
        tagToTraitMap.put(ConventionalBiomeTags.DESERT, BiomeTrait.DESERT);
        tagToTraitMap.put(ConventionalBiomeTags.RIVER, BiomeTrait.RIVER);
        tagToTraitMap.put(ConventionalBiomeTags.SWAMP, BiomeTrait.SWAMP);
        tagToTraitMap.put(ConventionalBiomeTags.MUSHROOM, BiomeTrait.MUSHROOM);
        tagToTraitMap.put(ConventionalBiomeTags.UNDERGROUND, BiomeTrait.UNDERGROUND);
        tagToTraitMap.put(ConventionalBiomeTags.MOUNTAIN, BiomeTrait.MOUNTAIN);
        tagToTraitMap.put(ConventionalBiomeTags.CLIMATE_HOT, BiomeTrait.HOT);
        tagToTraitMap.put(ConventionalBiomeTags.CLIMATE_TEMPERATE, BiomeTrait.TEMPERATE);
        tagToTraitMap.put(ConventionalBiomeTags.CLIMATE_COLD, BiomeTrait.COLD);
        tagToTraitMap.put(ConventionalBiomeTags.CLIMATE_DRY, BiomeTrait.DRY);
        tagToTraitMap.put(ConventionalBiomeTags.CLIMATE_WET, BiomeTrait.WET);
        tagToTraitMap.put(ConventionalBiomeTags.VEGETATION_SPARSE, BiomeTrait.SPARSE);
        tagToTraitMap.put(ConventionalBiomeTags.VEGETATION_DENSE, BiomeTrait.DENSE);
        tagToTraitMap.put(ConventionalBiomeTags.TREE_CONIFEROUS, BiomeTrait.CONIFEROUS);
        tagToTraitMap.put(ConventionalBiomeTags.TREE_SAVANNA, BiomeTrait.SAVANNA);
        tagToTraitMap.put(ConventionalBiomeTags.TREE_JUNGLE, BiomeTrait.JUNGLE);
        tagToTraitMap.put(ConventionalBiomeTags.TREE_DECIDUOUS, BiomeTrait.DECIDUOUS);
        tagToTraitMap.put(ConventionalBiomeTags.VOID, BiomeTrait.VOID);
        tagToTraitMap.put(ConventionalBiomeTags.MOUNTAIN_PEAK, BiomeTrait.MOUNTAIN);
        tagToTraitMap.put(ConventionalBiomeTags.MOUNTAIN_SLOPE, BiomeTrait.MOUNTAIN);
        tagToTraitMap.put(ConventionalBiomeTags.AQUATIC, BiomeTrait.WATER);
        tagToTraitMap.put(ConventionalBiomeTags.WASTELAND, BiomeTrait.WASTELAND);
        tagToTraitMap.put(ConventionalBiomeTags.DEAD, BiomeTrait.DEAD);
        tagToTraitMap.put(ConventionalBiomeTags.FLORAL, BiomeTrait.FLORAL);
        tagToTraitMap.put(ConventionalBiomeTags.SNOWY, BiomeTrait.SNOWY);
        tagToTraitMap.put(ConventionalBiomeTags.BADLANDS, BiomeTrait.BADLANDS);
        tagToTraitMap.put(ConventionalBiomeTags.CAVES, BiomeTrait.CAVES);
        tagToTraitMap.put(ConventionalBiomeTags.END_ISLANDS, BiomeTrait.THEEND);
        tagToTraitMap.put(ConventionalBiomeTags.NETHER_FORESTS, BiomeTrait.NETHER);
        tagToTraitMap.put(ConventionalBiomeTags.SNOWY_PLAINS, BiomeTrait.SNOWY);
        tagToTraitMap.put(ConventionalBiomeTags.STONY_SHORES, BiomeTrait.BEACH);
        tagToTraitMap.put(ConventionalBiomeTags.SHALLOW_OCEAN, BiomeTrait.OCEAN);
    }

    @Override
    public Collection<BiomeTrait> evaluate(Identifier id, Biome biome, RegistryEntry.Reference<Biome> biomeEntry) {
        Set<BiomeTrait> results = new HashSet<>();

        biomeEntry.streamTags().forEach(tag ->
        {
            var trait = tagToTraitMap.get(tag);
            if (trait != null)
                results.add(trait);
        });

        // Check for compounds
        if (TagHelpers.isIn(ConventionalBiomeTags.AQUATIC_ICY, biomeEntry)) {
            results.add(BiomeTrait.WATER);
            results.add(BiomeTrait.COLD);
        }

        if (TagHelpers.isIn(ConventionalBiomeTags.DEEP_OCEAN, biomeEntry)) {
            results.add(BiomeTrait.OCEAN);
            results.add(BiomeTrait.DEEP);
        }

        if (TagHelpers.isIn(ConventionalBiomeTags.FLOWER_FORESTS, biomeEntry)) {
            results.add(BiomeTrait.FLORAL);
            results.add(BiomeTrait.FOREST);
        }

        if (TagHelpers.isIn(ConventionalBiomeTags.WINDSWEPT, biomeEntry)) {
            results.add(BiomeTrait.EXTREME_HILLS);
        }

        return results;
    }
}

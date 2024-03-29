package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.tags.BiomeTags;

import java.util.*;

public class BiomeTagAnalyzer implements IBiomeTraitAnalyzer {

    private static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

    private static final Map<TagKey<Biome>, BiomeTrait> tagToTraitMap = new HashMap<>();

    static {
        tagToTraitMap.put(BiomeTags.IN_OVERWORLD, BiomeTrait.OVERWORLD);
        tagToTraitMap.put(BiomeTags.IN_THE_END, BiomeTrait.THEEND);
        tagToTraitMap.put(BiomeTags.IN_NETHER, BiomeTrait.NETHER);
        tagToTraitMap.put(BiomeTags.TAIGA, BiomeTrait.TAIGA);
        tagToTraitMap.put(BiomeTags.EXTREME_HILLS, BiomeTrait.EXTREME_HILLS);
        tagToTraitMap.put(BiomeTags.WINDSWEPT, BiomeTrait.WINDSWEPT);
        tagToTraitMap.put(BiomeTags.JUNGLE, BiomeTrait.JUNGLE);
        tagToTraitMap.put(BiomeTags.LUSH, BiomeTrait.LUSH);
        tagToTraitMap.put(BiomeTags.MESA, BiomeTrait.MESA);
        tagToTraitMap.put(BiomeTags.MAGICAL, BiomeTrait.MAGICAL);
        tagToTraitMap.put(BiomeTags.PLAINS, BiomeTrait.PLAINS);
        tagToTraitMap.put(BiomeTags.SAVANNA, BiomeTrait.SAVANNA);
        tagToTraitMap.put(BiomeTags.ICY, BiomeTrait.ICY);
        tagToTraitMap.put(BiomeTags.BEACH, BiomeTrait.BEACH);
        tagToTraitMap.put(BiomeTags.FOREST, BiomeTrait.FOREST);
        tagToTraitMap.put(BiomeTags.BIRCH_FOREST, BiomeTrait.FOREST);
        tagToTraitMap.put(BiomeTags.OCEAN, BiomeTrait.OCEAN);
        tagToTraitMap.put(BiomeTags.DESERT, BiomeTrait.DESERT);
        tagToTraitMap.put(BiomeTags.RARE, BiomeTrait.RARE);
        tagToTraitMap.put(BiomeTags.RIVER, BiomeTrait.RIVER);
        tagToTraitMap.put(BiomeTags.SANDY, BiomeTrait.SANDY);
        tagToTraitMap.put(BiomeTags.SWAMP, BiomeTrait.SWAMP);
        tagToTraitMap.put(BiomeTags.MUSHROOM, BiomeTrait.MUSHROOM);
        tagToTraitMap.put(BiomeTags.UNDERGROUND, BiomeTrait.UNDERGROUND);
        tagToTraitMap.put(BiomeTags.MOUNTAIN, BiomeTrait.MOUNTAIN);
        tagToTraitMap.put(BiomeTags.CLIMATE_HOT, BiomeTrait.HOT);
        tagToTraitMap.put(BiomeTags.CLIMATE_TEMPERATE, BiomeTrait.TEMPERATE);
        tagToTraitMap.put(BiomeTags.CLIMATE_COLD, BiomeTrait.COLD);
        tagToTraitMap.put(BiomeTags.CLIMATE_DRY, BiomeTrait.DRY);
        tagToTraitMap.put(BiomeTags.CLIMATE_WET, BiomeTrait.WET);
        tagToTraitMap.put(BiomeTags.VEGETATION_SPARSE, BiomeTrait.SPARSE);
        tagToTraitMap.put(BiomeTags.VEGETATION_DENSE, BiomeTrait.DENSE);
        tagToTraitMap.put(BiomeTags.TREE_CONIFEROUS, BiomeTrait.CONIFEROUS);
        tagToTraitMap.put(BiomeTags.TREE_SAVANNA, BiomeTrait.SAVANNA);
        tagToTraitMap.put(BiomeTags.TREE_JUNGLE, BiomeTrait.JUNGLE);
        tagToTraitMap.put(BiomeTags.TREE_DECIDUOUS, BiomeTrait.DECIDUOUS);
        tagToTraitMap.put(BiomeTags.VOID, BiomeTrait.VOID);
        tagToTraitMap.put(BiomeTags.MOUNTAIN_PEAK, BiomeTrait.MOUNTAIN);
        tagToTraitMap.put(BiomeTags.MOUNTAIN_SLOPE, BiomeTrait.MOUNTAIN);
        tagToTraitMap.put(BiomeTags.AQUATIC, BiomeTrait.WATER);
        tagToTraitMap.put(BiomeTags.WASTELAND, BiomeTrait.WASTELAND);
        tagToTraitMap.put(BiomeTags.DEAD, BiomeTrait.DEAD);
        tagToTraitMap.put(BiomeTags.FLORAL, BiomeTrait.FLORAL);
        tagToTraitMap.put(BiomeTags.SNOWY, BiomeTrait.SNOWY);
        tagToTraitMap.put(BiomeTags.SPOOKY, BiomeTrait.SPOOKY);
        tagToTraitMap.put(BiomeTags.BADLANDS, BiomeTrait.BADLANDS);
        tagToTraitMap.put(BiomeTags.CAVES, BiomeTrait.CAVES);
        tagToTraitMap.put(BiomeTags.END_ISLANDS, BiomeTrait.THEEND);
        tagToTraitMap.put(BiomeTags.NETHER_FORESTS, BiomeTrait.NETHER);
        tagToTraitMap.put(BiomeTags.SNOWY_PLAINS, BiomeTrait.SNOWY);
        tagToTraitMap.put(BiomeTags.STONY_SHORES, BiomeTrait.BEACH);
        tagToTraitMap.put(BiomeTags.SHALLOW_OCEAN, BiomeTrait.OCEAN);
    }

    @Override
    public Collection<BiomeTrait> evaluate(ResourceLocation id, Biome biome) {
        Set<BiomeTrait> results = new HashSet<>();

        // Have to do it this way so that the client side tagging has a chance.  When connecting to
        // vanilla servers, they will ONLY have the Minecraft tags, not the Fabric ones.
        for (var tagEntry : tagToTraitMap.entrySet())
            if (TAG_LIBRARY.is(tagEntry.getKey(), biome))
                results.add(tagEntry.getValue());

        // Check for compounds
        if (TAG_LIBRARY.is(BiomeTags.AQUATIC_ICY, biome)) {
            results.add(BiomeTrait.WATER);
            results.add(BiomeTrait.COLD);
        }

        if (TAG_LIBRARY.is(BiomeTags.DEEP_OCEAN, biome)) {
            results.add(BiomeTrait.OCEAN);
            results.add(BiomeTrait.DEEP);
        }

        if (TAG_LIBRARY.is(BiomeTags.FLOWER_FORESTS, biome)) {
            results.add(BiomeTrait.FLORAL);
            results.add(BiomeTrait.FOREST);
        }

        return results;
    }
}

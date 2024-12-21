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
        tagToTraitMap.put(BiomeTags.IS_OVERWORLD, BiomeTrait.OVERWORLD);
        tagToTraitMap.put(BiomeTags.IS_THE_END, BiomeTrait.THEEND);
        tagToTraitMap.put(BiomeTags.IS_NETHER, BiomeTrait.NETHER);
        tagToTraitMap.put(BiomeTags.IS_TAIGA, BiomeTrait.TAIGA);
        tagToTraitMap.put(BiomeTags.IS_HILL, BiomeTrait.HILLS);
        tagToTraitMap.put(BiomeTags.IS_WINDSWEPT, BiomeTrait.WINDSWEPT);
        tagToTraitMap.put(BiomeTags.IS_JUNGLE, BiomeTrait.JUNGLE);
        tagToTraitMap.put(BiomeTags.IS_LUSH, BiomeTrait.LUSH);
        tagToTraitMap.put(BiomeTags.IS_PLATEAU, BiomeTrait.PLATEAU);
        tagToTraitMap.put(BiomeTags.IS_MAGICAL, BiomeTrait.MAGICAL);
        tagToTraitMap.put(BiomeTags.IS_PLAINS, BiomeTrait.PLAINS);
        tagToTraitMap.put(BiomeTags.IS_SAVANNA, BiomeTrait.SAVANNA);
        tagToTraitMap.put(BiomeTags.IS_ICY, BiomeTrait.ICY);
        tagToTraitMap.put(BiomeTags.IS_BEACH, BiomeTrait.BEACH);
        tagToTraitMap.put(BiomeTags.IS_FOREST, BiomeTrait.FOREST);
        tagToTraitMap.put(BiomeTags.IS_BIRCH_FOREST, BiomeTrait.FOREST);
        tagToTraitMap.put(BiomeTags.IS_OCEAN, BiomeTrait.OCEAN);
        tagToTraitMap.put(BiomeTags.IS_DESERT, BiomeTrait.DESERT);
        tagToTraitMap.put(BiomeTags.IS_RARE, BiomeTrait.RARE);
        tagToTraitMap.put(BiomeTags.IS_RIVER, BiomeTrait.RIVER);
        tagToTraitMap.put(BiomeTags.IS_SANDY, BiomeTrait.SANDY);
        tagToTraitMap.put(BiomeTags.IS_SWAMP, BiomeTrait.SWAMP);
        tagToTraitMap.put(BiomeTags.IS_MUSHROOM, BiomeTrait.MUSHROOM);
        tagToTraitMap.put(BiomeTags.IS_UNDERGROUND, BiomeTrait.UNDERGROUND);
        tagToTraitMap.put(BiomeTags.IS_MOUNTAIN, BiomeTrait.MOUNTAIN);
        tagToTraitMap.put(BiomeTags.CLIMATE_HOT, BiomeTrait.HOT);
        tagToTraitMap.put(BiomeTags.CLIMATE_TEMPERATE, BiomeTrait.TEMPERATE);
        tagToTraitMap.put(BiomeTags.CLIMATE_COLD, BiomeTrait.COLD);
        tagToTraitMap.put(BiomeTags.CLIMATE_DRY, BiomeTrait.DRY);
        tagToTraitMap.put(BiomeTags.CLIMATE_WET, BiomeTrait.WET);
        tagToTraitMap.put(BiomeTags.IS_VEGETATION_SPARSE, BiomeTrait.SPARSE);
        tagToTraitMap.put(BiomeTags.IS_VEGETATION_DENSE, BiomeTrait.DENSE);
        tagToTraitMap.put(BiomeTags.IS_TREE_CONIFEROUS, BiomeTrait.CONIFEROUS);
        tagToTraitMap.put(BiomeTags.IS_TREE_SAVANNA, BiomeTrait.SAVANNA);
        tagToTraitMap.put(BiomeTags.IS_TREE_JUNGLE, BiomeTrait.JUNGLE);
        tagToTraitMap.put(BiomeTags.IS_TREE_DECIDUOUS, BiomeTrait.DECIDUOUS);
        tagToTraitMap.put(BiomeTags.IS_VOID, BiomeTrait.VOID);
        tagToTraitMap.put(BiomeTags.IS_MOUNTAIN_PEAK, BiomeTrait.MOUNTAIN);
        tagToTraitMap.put(BiomeTags.IS_MOUNTAIN_SLOPE, BiomeTrait.MOUNTAIN);
        tagToTraitMap.put(BiomeTags.IS_AQUATIC, BiomeTrait.WATER);
        tagToTraitMap.put(BiomeTags.IS_WASTELAND, BiomeTrait.WASTELAND);
        tagToTraitMap.put(BiomeTags.IS_DEAD, BiomeTrait.DEAD);
        tagToTraitMap.put(BiomeTags.IS_FLORAL, BiomeTrait.FLORAL);
        tagToTraitMap.put(BiomeTags.IS_SNOWY, BiomeTrait.SNOWY);
        tagToTraitMap.put(BiomeTags.IS_SPOOKY, BiomeTrait.SPOOKY);
        tagToTraitMap.put(BiomeTags.IS_BADLANDS, BiomeTrait.BADLANDS);
        tagToTraitMap.put(BiomeTags.IS_CAVE, BiomeTrait.CAVES);
        tagToTraitMap.put(BiomeTags.IS_END_ISLANDS, BiomeTrait.THEEND);
        tagToTraitMap.put(BiomeTags.IS_NETHER_FOREST, BiomeTrait.NETHER);
        tagToTraitMap.put(BiomeTags.IS_SNOWY_PLAINS, BiomeTrait.SNOWY);
        tagToTraitMap.put(BiomeTags.IS_STONY_SHORES, BiomeTrait.BEACH);
        tagToTraitMap.put(BiomeTags.IS_SHALLOW_OCEAN, BiomeTrait.OCEAN);
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
        if (TAG_LIBRARY.is(BiomeTags.IS_AQUATIC_ICY, biome)) {
            results.add(BiomeTrait.WATER);
            results.add(BiomeTrait.COLD);
        }

        if (TAG_LIBRARY.is(BiomeTags.IS_DEEP_OCEAN, biome)) {
            results.add(BiomeTrait.OCEAN);
            results.add(BiomeTrait.DEEP);
        }

        if (TAG_LIBRARY.is(BiomeTags.IS_FLOWER_FOREST, biome)) {
            results.add(BiomeTrait.FLORAL);
            results.add(BiomeTrait.FOREST);
        }

        return results;
    }
}

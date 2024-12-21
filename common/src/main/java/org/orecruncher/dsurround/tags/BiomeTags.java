package org.orecruncher.dsurround.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.Constants;

import java.util.Collection;
import java.util.HashSet;

public class BiomeTags {

    static final Collection<TagKey<Biome>> TAGS = new HashSet<>();

    public static final TagKey<Biome> IS_AQUATIC = of("is_aquatic");
    public static final TagKey<Biome> IS_AQUATIC_ICY = of("is_aquatic_icy");
    public static final TagKey<Biome> IS_BADLANDS = of("is_badlands");
    public static final TagKey<Biome> IS_BEACH = of("is_beach");
    public static final TagKey<Biome> IS_BIRCH_FOREST = of("is_birch_forest");
    public static final TagKey<Biome> IS_CAVE = of("is_cave");
    public static final TagKey<Biome> CLIMATE_COLD = of("climate_cold");
    public static final TagKey<Biome> CLIMATE_DRY = of("climate_dry");
    public static final TagKey<Biome> CLIMATE_HOT = of("climate_hot");
    public static final TagKey<Biome> CLIMATE_TEMPERATE = of("climate_temperate");
    public static final TagKey<Biome> CLIMATE_WET = of("climate_wet");
    public static final TagKey<Biome> IS_DEAD = of("is_dead");
    public static final TagKey<Biome> IS_DEEP_OCEAN = of("is_deep_ocean");
    public static final TagKey<Biome> IS_DESERT = of("is_desert");
    public static final TagKey<Biome> IS_END_ISLANDS = of("is_end_islands");
    public static final TagKey<Biome> IS_HILL = of("is_hill");
    public static final TagKey<Biome> IS_FLORAL = of("is_floral");
    public static final TagKey<Biome> IS_FLOWER_FOREST = of("is_flower_forest");
    public static final TagKey<Biome> IS_FOREST = of("is_forest");
    public static final TagKey<Biome> IS_ICY = of("is_icy");
    public static final TagKey<Biome> IS_NETHER = of("is_nether");
    public static final TagKey<Biome> IS_OVERWORLD = of("is_overworld");
    public static final TagKey<Biome> IS_THE_END = of("is_the_end");
    public static final TagKey<Biome> IS_JUNGLE = of("is_jungle");
    public static final TagKey<Biome> IS_LUSH = of("is_lush");
    public static final TagKey<Biome> IS_MAGICAL = of("is_magical");
    public static final TagKey<Biome> IS_PLATEAU = of("is_plateau");
    public static final TagKey<Biome> IS_MOUNTAIN = of("is_mountain");
    public static final TagKey<Biome> IS_MOUNTAIN_PEAK = of("is_mountain_peak");
    public static final TagKey<Biome> IS_MOUNTAIN_SLOPE = of("is_mountain_slope");
    public static final TagKey<Biome> IS_MUSHROOM = of("is_mushroom");
    public static final TagKey<Biome> IS_NETHER_FOREST = of("is_nether_forest");
    public static final TagKey<Biome> IS_OCEAN = of("is_ocean");
    public static final TagKey<Biome> IS_PLAINS = of("is_plains");
    public static final TagKey<Biome> IS_RARE = of("is_rare");
    public static final TagKey<Biome> IS_RIVER = of("is_river");
    public static final TagKey<Biome> IS_SANDY = of("is_sandy");
    public static final TagKey<Biome> IS_SAVANNA = of("is_savanna");
    public static final TagKey<Biome> IS_SHALLOW_OCEAN = of("is_shallow_ocean");
    public static final TagKey<Biome> IS_SNOWY = of("is_snowy");
    public static final TagKey<Biome> IS_SNOWY_PLAINS = of("is_snowy_plains");
    public static final TagKey<Biome> IS_SPOOKY = of("is_spooky");
    public static final TagKey<Biome> IS_STONY_SHORES = of("is_stony_shores");
    public static final TagKey<Biome> IS_SWAMP = of("is_swamp");
    public static final TagKey<Biome> IS_TAIGA = of("is_taiga");
    public static final TagKey<Biome> IS_TREE_CONIFEROUS = of("is_tree_coniferous");
    public static final TagKey<Biome> IS_TREE_DECIDUOUS = of("is_tree_deciduous");
    public static final TagKey<Biome> IS_TREE_JUNGLE = of("is_tree_jungle");
    public static final TagKey<Biome> IS_TREE_SAVANNA = of("is_tree_savanna");
    public static final TagKey<Biome> IS_UNDERGROUND = of("is_underground");
    public static final TagKey<Biome> IS_VEGETATION_DENSE = of("is_vegetation_dense");
    public static final TagKey<Biome> IS_VEGETATION_SPARSE = of("is_vegetation_sparse");
    public static final TagKey<Biome> IS_VOID = of("is_void");
    public static final TagKey<Biome> IS_WASTELAND = of("is_wasteland");
    public static final TagKey<Biome> IS_WINDSWEPT = of("is_windswept");

    private static TagKey<Biome> of(String id) {
        var tagKey = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, id));
        TAGS.add(tagKey);
        return tagKey;
    }
}

package org.orecruncher.dsurround.tags;

import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.Constants;

public class BiomeTags {

    public static final TagKey<Biome> AQUATIC = of("aquatic");
    public static final TagKey<Biome> AQUATIC_ICY = of("aquatic_icy");
    public static final TagKey<Biome> BADLANDS = of("badlands");
    public static final TagKey<Biome> BEACH = of("beach");
    public static final TagKey<Biome> BIRCH_FOREST = of("birch_forest");
    public static final TagKey<Biome> CAVES = of("caves");
    public static final TagKey<Biome> CLIMATE_COLD = of("climate_cold");
    public static final TagKey<Biome> CLIMATE_DRY = of("climate_dry");
    public static final TagKey<Biome> CLIMATE_HOT = of("climate_hot");
    public static final TagKey<Biome> CLIMATE_TEMPERATE = of("climate_temperate");
    public static final TagKey<Biome> CLIMATE_WET = of("climate_wet");
    public static final TagKey<Biome> DEAD = of("dead");
    public static final TagKey<Biome> DEEP_OCEAN = of("deep_ocean");
    public static final TagKey<Biome> DESERT = of("desert");
    public static final TagKey<Biome> END_ISLANDS = of("end_islands");
    public static final TagKey<Biome> EXTREME_HILLS = of("extreme_hills");
    public static final TagKey<Biome> FLORAL = of("floral");
    public static final TagKey<Biome> FLOWER_FORESTS = of("flower_forests");
    public static final TagKey<Biome> FOREST = of("forest");
    public static final TagKey<Biome> ICY = of("icy");
    public static final TagKey<Biome> IN_NETHER = of("in_nether");
    public static final TagKey<Biome> IN_OVERWORLD = of("in_overworld");
    public static final TagKey<Biome> IN_THE_END = of("in_the_end");
    public static final TagKey<Biome> JUNGLE = of("jungle");
    public static final TagKey<Biome> MESA = of("mesa");
    public static final TagKey<Biome> MOUNTAIN = of("mountain");
    public static final TagKey<Biome> MOUNTAIN_PEAK = of("mountain_peak");
    public static final TagKey<Biome> MOUNTAIN_SLOPE = of("mountain_slope");
    public static final TagKey<Biome> MUSHROOM = of("mushroom");
    public static final TagKey<Biome> NETHER_FORESTS = of("nether_forests");
    public static final TagKey<Biome> OCEAN = of("ocean");
    public static final TagKey<Biome> PLAINS = of("plains");
    public static final TagKey<Biome> RIVER = of("river");
    public static final TagKey<Biome> SAVANNA = of("savanna");
    public static final TagKey<Biome> SHALLOW_OCEAN = of("shallow_ocean");
    public static final TagKey<Biome> SNOWY = of("snowy");
    public static final TagKey<Biome> SNOWY_PLAINS = of("snowy_plains");
    public static final TagKey<Biome> STONY_SHORES = of("stony_shores");
    public static final TagKey<Biome> SWAMP = of("swamp");
    public static final TagKey<Biome> TAIGA = of("taiga");
    public static final TagKey<Biome> TREE_CONIFEROUS = of("tree_coniferous");
    public static final TagKey<Biome> TREE_DECIDUOUS = of("tree_deciduous");
    public static final TagKey<Biome> TREE_JUNGLE = of("tree_jungle");
    public static final TagKey<Biome> TREE_SAVANNA = of("tree_savanna");
    public static final TagKey<Biome> UNDERGROUND = of("underground");
    public static final TagKey<Biome> VEGETATION_DENSE = of("vegetation_dense");
    public static final TagKey<Biome> VEGETATION_SPARSE = of("vegetation_sparse");
    public static final TagKey<Biome> VOID = of("void");
    public static final TagKey<Biome> WASTELAND = of("wasteland");
    public static final TagKey<Biome> WINDSWEPT = of("windswept");

    private static TagKey<Biome> of(String id) {
        return TagKey.of(RegistryKeys.BIOME, new Identifier(Constants.MOD_ID, id));
    }

}

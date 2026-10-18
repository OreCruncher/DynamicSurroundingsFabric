package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;
import static org.orecruncher.dsurround.config.BiomeTrait.*;

/**
 * The traits of each vanilla biome, as the mod sees them. A vanilla biome gets these rather than guesses: its tags
 * are added to them, and nothing is worked out from its effects, climate or name.
 */
public final class VanillaBiomeAnalyzer implements IBiomeTraitAnalyzer {

    static final Map<String, Set<BiomeTrait>> VANILLA_TRAITS = Map.<String, Set<BiomeTrait>>ofEntries(
            entry("plains", traits(OVERWORLD, PLAINS, TEMPERATE, SPARSE_VEGETATION)),
            entry("sunflower_plains", traits(OVERWORLD, PLAINS, TEMPERATE, FLORAL, SPARSE_VEGETATION)),
            entry("snowy_plains", traits(OVERWORLD, PLAINS, SNOWY_PLAINS, COLD, SNOWY, SPARSE_VEGETATION)),
            entry("ice_spikes", traits(OVERWORLD, PLAINS, COLD, SNOWY, ICY, RARE, SPARSE_VEGETATION)),
            entry("desert", traits(OVERWORLD, DESERT, HOT, DRY, SANDY, SPARSE_VEGETATION)),
            entry("swamp", traits(OVERWORLD, SWAMP, TEMPERATE, WET, DENSE_VEGETATION)),
            entry("mangrove_swamp", traits(OVERWORLD, SWAMP, TEMPERATE, WET, DENSE_VEGETATION)),
            entry("forest", traits(OVERWORLD, FOREST, TEMPERATE, DECIDUOUS, DENSE_VEGETATION)),
            entry("flower_forest", traits(OVERWORLD, FOREST, FLOWER_FOREST, TEMPERATE, DECIDUOUS, FLORAL, DENSE_VEGETATION)),
            entry("birch_forest", traits(OVERWORLD, FOREST, BIRCH_FOREST, TEMPERATE, DECIDUOUS, DENSE_VEGETATION)),
            entry("dark_forest", traits(OVERWORLD, FOREST, DARK_FOREST, TEMPERATE, DECIDUOUS, DENSE_VEGETATION, SPOOKY)),
            entry("old_growth_birch_forest", traits(OVERWORLD, FOREST, BIRCH_FOREST, OLD_GROWTH, TEMPERATE, DECIDUOUS, DENSE_VEGETATION, RARE)),
            entry("old_growth_pine_taiga", traits(OVERWORLD, FOREST, TAIGA, OLD_GROWTH, COLD, CONIFEROUS, DENSE_VEGETATION, RARE)),
            entry("old_growth_spruce_taiga", traits(OVERWORLD, FOREST, TAIGA, OLD_GROWTH, COLD, CONIFEROUS, DENSE_VEGETATION, RARE)),
            entry("taiga", traits(OVERWORLD, FOREST, TAIGA, COLD, CONIFEROUS, DENSE_VEGETATION)),
            entry("snowy_taiga", traits(OVERWORLD, FOREST, TAIGA, COLD, SNOWY, CONIFEROUS, DENSE_VEGETATION)),
            entry("savanna", traits(OVERWORLD, SAVANNA, HOT, DRY, SPARSE_VEGETATION)),
            entry("savanna_plateau", traits(OVERWORLD, SAVANNA, PLATEAU, HOT, DRY, SPARSE_VEGETATION)),
            entry("windswept_hills", traits(OVERWORLD, HILL, MOUNTAIN, WINDSWEPT, TEMPERATE, SPARSE_VEGETATION)),
            entry("windswept_gravelly_hills", traits(OVERWORLD, HILL, MOUNTAIN, WINDSWEPT, TEMPERATE, SPARSE_VEGETATION)),
            entry("windswept_forest", traits(OVERWORLD, FOREST, HILL, MOUNTAIN, WINDSWEPT, TEMPERATE, DECIDUOUS, SPARSE_VEGETATION)),
            entry("windswept_savanna", traits(OVERWORLD, SAVANNA, HILL, WINDSWEPT, HOT, DRY, SPARSE_VEGETATION)),
            entry("jungle", traits(OVERWORLD, JUNGLE, FOREST, HOT, WET, DENSE_VEGETATION)),
            entry("sparse_jungle", traits(OVERWORLD, JUNGLE, FOREST, HOT, WET, SPARSE_VEGETATION)),
            entry("bamboo_jungle", traits(OVERWORLD, JUNGLE, FOREST, HOT, WET, DENSE_VEGETATION)),
            entry("badlands", traits(OVERWORLD, BADLANDS, WASTELAND, HOT, DRY, SPARSE_VEGETATION, SANDY)),
            entry("eroded_badlands", traits(OVERWORLD, BADLANDS, WASTELAND, HOT, DRY, SPARSE_VEGETATION, SANDY, RARE)),
            entry("wooded_badlands", traits(OVERWORLD, BADLANDS, WASTELAND, HOT, DRY, SPARSE_VEGETATION, SANDY)),
            entry("meadow", traits(OVERWORLD, PLAINS, MOUNTAIN, TEMPERATE, FLORAL, SPARSE_VEGETATION)),
            entry("cherry_grove", traits(OVERWORLD, FOREST, MOUNTAIN, TEMPERATE, FLORAL, DECIDUOUS, DENSE_VEGETATION)),
            entry("grove", traits(OVERWORLD, FOREST, MOUNTAIN, COLD, SNOWY, CONIFEROUS, DENSE_VEGETATION)),
            entry("snowy_slopes", traits(OVERWORLD, MOUNTAIN, COLD, SNOWY, SPARSE_VEGETATION)),
            entry("frozen_peaks", traits(OVERWORLD, MOUNTAIN, COLD, SNOWY, ICY, SPARSE_VEGETATION)),
            entry("jagged_peaks", traits(OVERWORLD, MOUNTAIN, COLD, SNOWY, SPARSE_VEGETATION)),
            entry("stony_peaks", traits(OVERWORLD, MOUNTAIN, HOT, DRY, SPARSE_VEGETATION)),
            entry("river", traits(OVERWORLD, RIVER, WET, TEMPERATE)),
            entry("frozen_river", traits(OVERWORLD, RIVER, WET, COLD, SNOWY, ICY)),
            entry("beach", traits(OVERWORLD, BEACH, WET, TEMPERATE, SANDY, SPARSE_VEGETATION)),
            entry("snowy_beach", traits(OVERWORLD, BEACH, WET, COLD, SNOWY, SANDY, SPARSE_VEGETATION)),
            entry("stony_shore", traits(OVERWORLD, BEACH, STONY_SHORES, WET, TEMPERATE, SPARSE_VEGETATION)),
            entry("warm_ocean", traits(OVERWORLD, OCEAN, WET, HOT, WET)),
            entry("lukewarm_ocean", traits(OVERWORLD, OCEAN, WET, TEMPERATE, WET)),
            entry("deep_lukewarm_ocean", traits(OVERWORLD, OCEAN, WET, DEEP_OCEAN, TEMPERATE, WET)),
            entry("ocean", traits(OVERWORLD, OCEAN, WET, TEMPERATE, WET)),
            entry("deep_ocean", traits(OVERWORLD, OCEAN, WET, DEEP_OCEAN, TEMPERATE, WET)),
            entry("cold_ocean", traits(OVERWORLD, OCEAN, WET, COLD, WET)),
            entry("deep_cold_ocean", traits(OVERWORLD, OCEAN, WET, DEEP_OCEAN, COLD, WET)),
            entry("frozen_ocean", traits(OVERWORLD, OCEAN, WET, COLD, WET, ICY)),
            entry("deep_frozen_ocean", traits(OVERWORLD, OCEAN, WET, DEEP_OCEAN, COLD, WET, ICY)),
            entry("mushroom_fields", traits(OVERWORLD, MUSHROOM, MAGICAL, TEMPERATE, RARE, DENSE_VEGETATION)),
            entry("dripstone_caves", traits(OVERWORLD, UNDERGROUND, CAVE, TEMPERATE, WET)),
            entry("lush_caves", traits(OVERWORLD, UNDERGROUND, CAVE, LUSH, TEMPERATE, WET, DENSE_VEGETATION)),
            entry("deep_dark", traits(OVERWORLD, UNDERGROUND, CAVE, SPOOKY, RARE, TEMPERATE)),
            entry("pale_garden", traits(OVERWORLD, FOREST, DARK_FOREST, SPOOKY, RARE, TEMPERATE, DECIDUOUS, DENSE_VEGETATION)),
            entry("sulfur_caves", traits(OVERWORLD, UNDERGROUND, CAVE, HOT, DRY, WASTELAND, RARE)),
            entry("nether_wastes", traits(NETHER, HOT, DRY, WASTELAND)),
            entry("crimson_forest", traits(NETHER, NETHER_FOREST, HOT, DRY)),
            entry("warped_forest", traits(NETHER, NETHER_FOREST, HOT, DRY)),
            entry("soul_sand_valley", traits(NETHER, HOT, DRY, WASTELAND, SPOOKY)),
            entry("basalt_deltas", traits(NETHER, HOT, DRY, WASTELAND)),
            entry("the_end", traits(END, DRY, WASTELAND)),
            entry("end_highlands", traits(END, OUTER_END_ISLAND, DRY, WASTELAND)),
            entry("end_midlands", traits(END, OUTER_END_ISLAND, DRY, WASTELAND)),
            entry("small_end_islands", traits(END, DRY, WASTELAND)),
            entry("end_barrens", traits(END, OUTER_END_ISLAND, DRY, WASTELAND)),
            entry("the_void", traits(VOID, DRY, WASTELAND))
    );

    /**
     * Whether the table has the biome
     */
    static boolean covers(Identifier id) {
        return "minecraft".equals(id.getNamespace()) && VANILLA_TRAITS.containsKey(id.getPath());
    }

    @Override
    public String name() {
        return "VanillaBiomeAnalyzer";
    }

    @Override
    public void analyze(@NotNull BiomeEvidence biome, @NotNull Set<BiomeTrait> resultCollection) {
        if ("minecraft".equals(biome.id().getNamespace())) {
            var traits = VANILLA_TRAITS.get(biome.id().getPath());
            if (traits != null)
                resultCollection.addAll(traits);
        }
    }

    private static Set<BiomeTrait> traits(BiomeTrait... traits) {
        var result = EnumSet.noneOf(BiomeTrait.class);
        Collections.addAll(result, traits);
        return result;
    }
}

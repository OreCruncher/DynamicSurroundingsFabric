package org.orecruncher.dsurround.config.biome.biometraits;

import dev.architectury.hooks.level.biome.BiomeHooks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.util.*;

import static java.util.Map.entry;
import static org.orecruncher.dsurround.config.BiomeTrait.*;

/**
 * Generates biome traits for a biome based on the biome ID
 */
public class BiomeNameFallbackAnalyzer implements IBiomeTraitAnalyzer {

    private static final Map<String, Set<BiomeTrait>> VANILLA_TRAITS = Map.<String, Set<BiomeTrait>>ofEntries(
            entry("plains", traits(OVERWORLD, PLAINS, TEMPERATE, SPARSE_VEGETATION)),
            entry("sunflower_plains", traits(OVERWORLD, PLAINS, TEMPERATE, FLORAL, SPARSE_VEGETATION)),
            entry("snowy_plains", traits(OVERWORLD, PLAINS, COLD, SNOWY, SPARSE_VEGETATION)),
            entry("ice_spikes", traits(OVERWORLD, PLAINS, COLD, SNOWY, ICY, RARE, SPARSE_VEGETATION)),
            entry("desert", traits(OVERWORLD, DESERT, HOT, DRY, SANDY, SPARSE_VEGETATION)),
            entry("swamp", traits(OVERWORLD, SWAMP, TEMPERATE, WET, DENSE_VEGETATION)),
            entry("mangrove_swamp", traits(OVERWORLD, SWAMP, HOT, WET, DENSE_VEGETATION)),
            entry("forest", traits(OVERWORLD, FOREST, TEMPERATE, DECIDUOUS, DENSE_VEGETATION)),
            entry("flower_forest", traits(OVERWORLD, FOREST, TEMPERATE, DECIDUOUS, FLORAL, DENSE_VEGETATION)),
            entry("birch_forest", traits(OVERWORLD, FOREST, TEMPERATE, DECIDUOUS, DENSE_VEGETATION)),
            entry("dark_forest", traits(OVERWORLD, FOREST, TEMPERATE, DECIDUOUS, DENSE_VEGETATION, SPOOKY)),
            entry("old_growth_birch_forest", traits(OVERWORLD, FOREST, TEMPERATE, DECIDUOUS, DENSE_VEGETATION, RARE)),
            entry("old_growth_pine_taiga", traits(OVERWORLD, FOREST, TAIGA, COLD, CONIFEROUS, DENSE_VEGETATION, RARE)),
            entry("old_growth_spruce_taiga", traits(OVERWORLD, FOREST, TAIGA, COLD, CONIFEROUS, DENSE_VEGETATION, RARE)),
            entry("taiga", traits(OVERWORLD, FOREST, TAIGA, COLD, CONIFEROUS, DENSE_VEGETATION)),
            entry("snowy_taiga", traits(OVERWORLD, FOREST, TAIGA, COLD, SNOWY, CONIFEROUS, DENSE_VEGETATION)),
            entry("savanna", traits(OVERWORLD, SAVANNA, HOT, DRY, SPARSE_VEGETATION)),
            entry("savanna_plateau", traits(OVERWORLD, SAVANNA, PLATEAU, HOT, DRY, SPARSE_VEGETATION)),
            entry("windswept_hills", traits(OVERWORLD, HILL, MOUNTAIN, WINDSWEPT, TEMPERATE, SPARSE_VEGETATION)),
            entry("windswept_gravelly_hills", traits(OVERWORLD, HILL, MOUNTAIN, WINDSWEPT, TEMPERATE, SPARSE_VEGETATION)),
            entry("windswept_forest", traits(OVERWORLD, FOREST, HILL, MOUNTAIN, WINDSWEPT, TEMPERATE, SPARSE_VEGETATION)),
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
            entry("stony_shore", traits(OVERWORLD, BEACH, WET, TEMPERATE, SPARSE_VEGETATION)),
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
            entry("dripstone_caves", traits(OVERWORLD, UNDERGROUND, CAVE, TEMPERATE, DRY)),
            entry("lush_caves", traits(OVERWORLD, UNDERGROUND, CAVE, LUSH, TEMPERATE, WET, DENSE_VEGETATION)),
            entry("deep_dark", traits(OVERWORLD, UNDERGROUND, CAVE, SPOOKY, RARE, TEMPERATE)),
            entry("pale_garden", traits(OVERWORLD, FOREST, SPOOKY, RARE, TEMPERATE, DECIDUOUS, DENSE_VEGETATION)),
            entry("sulfur_caves", traits(OVERWORLD, UNDERGROUND, CAVE, HOT, DRY, WASTELAND, RARE)),
            entry("nether_wastes", traits(NETHER, HOT, DRY, WASTELAND)),
            entry("crimson_forest", traits(NETHER, HOT, DRY)),
            entry("warped_forest", traits(NETHER, HOT, DRY)),
            entry("soul_sand_valley", traits(NETHER, HOT, DRY, WASTELAND, SPOOKY)),
            entry("basalt_deltas", traits(NETHER, HOT, DRY, WASTELAND)),
            entry("the_end", traits(END, DRY, WASTELAND)),
            entry("end_highlands", traits(END, DRY, WASTELAND)),
            entry("end_midlands", traits(END, DRY, WASTELAND)),
            entry("small_end_islands", traits(END, DRY, WASTELAND)),
            entry("end_barrens", traits(END, DRY, WASTELAND)),
            entry("the_void", traits(VOID, DRY, WASTELAND))
    );

    @Override
    public String name() {
        return "BiomeNameFallbackAnalyzer";
    }

    @Override
    public void analyze(@NotNull ResourceLocation id, @NotNull Biome biome, @NotNull Set<BiomeTrait> resultCollection) {
        String path = id.getPath().toLowerCase(Locale.ROOT);

        // If it is a vanilla biome it should be in our map
        if ("minecraft".equals(id.getNamespace())) {
            var vanillaTraits = VANILLA_TRAITS.get(path);
            if (vanillaTraits != null) {
                resultCollection.addAll(vanillaTraits);
                return;
            }
        }

        // Only do the following analysis if it was not found in the map
        addNameBasedTraits(path, resultCollection);
        addClimateTraits(biome, resultCollection);
    }

    private static void addNameBasedTraits(String path, @NotNull Set<BiomeTrait> results) {
        if (path.contains("nether")) {
            results.add(NETHER);
            results.add(HOT);
            results.add(DRY);
        }

        if (path.contains("end") && !path.contains("endless")) {
            results.add(END);
            results.add(DRY);
        }

        if (path.contains("cave") || path.contains("caves") || path.contains("underground")) {
            results.add(UNDERGROUND);
            results.add(CAVE);
        }

        if (path.contains("sulfur") || path.contains("sulphur")) {
            results.add(OVERWORLD);
            results.add(UNDERGROUND);
            results.add(CAVE);
            results.add(HOT);
            results.add(DRY);
            results.add(WASTELAND);
        }

        if (path.contains("ocean")) {
            results.add(OVERWORLD);
            results.add(OCEAN);
            results.add(WET);
            if (path.contains("deep")) {
                results.add(DEEP_OCEAN);
            }
        }

        if (path.contains("river")) {
            results.add(OVERWORLD);
            results.add(RIVER);
            results.add(WET);
        }

        if (path.contains("forest") || path.contains("woods") || path.contains("grove")) {
            results.add(OVERWORLD);
            results.add(FOREST);
            results.add(DENSE_VEGETATION);
        }

        if (path.contains("taiga") || path.contains("pine") || path.contains("spruce")) {
            results.add(TAIGA);
            results.add(CONIFEROUS);
        }

        if (path.contains("jungle")) {
            results.add(JUNGLE);
            results.add(HOT);
            results.add(WET);
        }

        if (path.contains("swamp") || path.contains("bog") || path.contains("marsh")) {
            results.add(SWAMP);
            results.add(WET);
        }

        if (path.contains("desert")) {
            results.add(DESERT);
            results.add(HOT);
            results.add(DRY);
            results.add(SANDY);
        }

        if (path.contains("badlands") || path.contains("wasteland")) {
            results.add(BADLANDS);
            results.add(WASTELAND);
            results.add(HOT);
            results.add(DRY);
        }

        if (path.contains("beach") || path.contains("shore")) {
            results.add(OVERWORLD);
            results.add(BEACH);
            results.add(WET);
        }

        if (path.contains("snow") || path.contains("frozen") || path.contains("ice")) {
            results.add(COLD);
            results.add(SNOWY);
        }

        if (path.contains("peak") || path.contains("slope") || path.contains("mountain")) {
            results.add(MOUNTAIN);
        }

        if (path.contains("flower") || path.contains("meadow") || path.contains("cherry")) {
            results.add(FLORAL);
        }

        if (path.contains("dark") || path.contains("ominous") || path.contains("pale")) {
            results.add(SPOOKY);
        }

        if (path.contains("magic") || path.contains("magik")) {
            results.add(MAGICAL);
        }
    }

    private static void addClimateTraits(Biome biome, Set<BiomeTrait> results) {
        // Initialization of BiomeInfo depends on this routine. We need to use the
        // biome property information directly.
        var properties = BiomeHooks.getBiomeProperties(biome);
        float downfall = properties.getClimateProperties().getDownfall();
        float temperature = properties.getClimateProperties().getTemperature();

        if (temperature <= 0.15F) {
            results.add(COLD);
        } else if (temperature >= 0.95F) {
            results.add(HOT);
        } else {
            results.add(TEMPERATE);
        }

        if (downfall <= 0.15F) {
            results.add(DRY);
        } else if (downfall >= 0.70F) {
            results.add(WET);
        }
    }

    private static Set<BiomeTrait> traits(BiomeTrait... traits) {
        var result = EnumSet.noneOf(BiomeTrait.class);
        Collections.addAll(result, traits);
        return result;
    }
}
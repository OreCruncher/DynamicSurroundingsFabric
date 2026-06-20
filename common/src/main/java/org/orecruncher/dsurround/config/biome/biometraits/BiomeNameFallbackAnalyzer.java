package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.lib.BiomeCompat;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;
import static org.orecruncher.dsurround.config.BiomeTrait.*;

/**
 * Supplements tag based biome detection with stable id and climate based fallbacks.
 *
 * <p>Client side biome tags can be incomplete on vanilla servers or immediately after a
 * Minecraft version upgrade. This analyzer keeps Dynamic Surroundings usable for new
 * vanilla biomes while the tag set catches up, and it gives modded biomes a reasonable
 * first-pass classification from their registry path and climate values.</p>
 */
public class BiomeNameFallbackAnalyzer implements IBiomeTraitAnalyzer {

    private static final Map<String, List<BiomeTrait>> VANILLA_TRAITS = Map.ofEntries(
            entry("plains", traits(OVERWORLD, PLAINS, TEMPERATE, SPARSE)),
            entry("sunflower_plains", traits(OVERWORLD, PLAINS, TEMPERATE, FLORAL, SPARSE)),
            entry("snowy_plains", traits(OVERWORLD, PLAINS, COLD, SNOWY, SPARSE)),
            entry("ice_spikes", traits(OVERWORLD, PLAINS, COLD, SNOWY, ICY, RARE, SPARSE)),
            entry("desert", traits(OVERWORLD, DESERT, HOT, DRY, SANDY, SPARSE)),
            entry("swamp", traits(OVERWORLD, SWAMP, TEMPERATE, WET, DENSE)),
            entry("mangrove_swamp", traits(OVERWORLD, SWAMP, HOT, WET, DENSE)),
            entry("forest", traits(OVERWORLD, FOREST, TEMPERATE, DECIDUOUS, DENSE)),
            entry("flower_forest", traits(OVERWORLD, FOREST, TEMPERATE, DECIDUOUS, FLORAL, DENSE)),
            entry("birch_forest", traits(OVERWORLD, FOREST, TEMPERATE, DECIDUOUS, DENSE)),
            entry("dark_forest", traits(OVERWORLD, FOREST, TEMPERATE, DECIDUOUS, DENSE, SPOOKY)),
            entry("old_growth_birch_forest", traits(OVERWORLD, FOREST, TEMPERATE, DECIDUOUS, DENSE, RARE)),
            entry("old_growth_pine_taiga", traits(OVERWORLD, FOREST, TAIGA, COLD, CONIFEROUS, DENSE, RARE)),
            entry("old_growth_spruce_taiga", traits(OVERWORLD, FOREST, TAIGA, COLD, CONIFEROUS, DENSE, RARE)),
            entry("taiga", traits(OVERWORLD, FOREST, TAIGA, COLD, CONIFEROUS, DENSE)),
            entry("snowy_taiga", traits(OVERWORLD, FOREST, TAIGA, COLD, SNOWY, CONIFEROUS, DENSE)),
            entry("savanna", traits(OVERWORLD, SAVANNA, HOT, DRY, SPARSE)),
            entry("savanna_plateau", traits(OVERWORLD, SAVANNA, PLATEAU, HOT, DRY, SPARSE)),
            entry("windswept_hills", traits(OVERWORLD, HILLS, MOUNTAIN, WINDSWEPT, TEMPERATE, SPARSE)),
            entry("windswept_gravelly_hills", traits(OVERWORLD, HILLS, MOUNTAIN, WINDSWEPT, TEMPERATE, SPARSE)),
            entry("windswept_forest", traits(OVERWORLD, FOREST, HILLS, MOUNTAIN, WINDSWEPT, TEMPERATE, SPARSE)),
            entry("windswept_savanna", traits(OVERWORLD, SAVANNA, HILLS, WINDSWEPT, HOT, DRY, SPARSE)),
            entry("jungle", traits(OVERWORLD, JUNGLE, FOREST, HOT, WET, DENSE)),
            entry("sparse_jungle", traits(OVERWORLD, JUNGLE, FOREST, HOT, WET, SPARSE)),
            entry("bamboo_jungle", traits(OVERWORLD, JUNGLE, FOREST, HOT, WET, DENSE)),
            entry("badlands", traits(OVERWORLD, BADLANDS, WASTELAND, HOT, DRY, SPARSE, SANDY)),
            entry("eroded_badlands", traits(OVERWORLD, BADLANDS, WASTELAND, HOT, DRY, SPARSE, SANDY, RARE)),
            entry("wooded_badlands", traits(OVERWORLD, BADLANDS, WASTELAND, HOT, DRY, SPARSE, SANDY)),
            entry("meadow", traits(OVERWORLD, PLAINS, MOUNTAIN, TEMPERATE, FLORAL, SPARSE)),
            entry("cherry_grove", traits(OVERWORLD, FOREST, MOUNTAIN, TEMPERATE, FLORAL, DECIDUOUS, DENSE)),
            entry("grove", traits(OVERWORLD, FOREST, MOUNTAIN, COLD, SNOWY, CONIFEROUS, DENSE)),
            entry("snowy_slopes", traits(OVERWORLD, MOUNTAIN, COLD, SNOWY, SPARSE)),
            entry("frozen_peaks", traits(OVERWORLD, MOUNTAIN, COLD, SNOWY, ICY, SPARSE)),
            entry("jagged_peaks", traits(OVERWORLD, MOUNTAIN, COLD, SNOWY, SPARSE)),
            entry("stony_peaks", traits(OVERWORLD, MOUNTAIN, HOT, DRY, SPARSE)),
            entry("river", traits(OVERWORLD, RIVER, WATER, TEMPERATE)),
            entry("frozen_river", traits(OVERWORLD, RIVER, WATER, COLD, SNOWY, ICY)),
            entry("beach", traits(OVERWORLD, BEACH, WATER, TEMPERATE, SANDY, SPARSE)),
            entry("snowy_beach", traits(OVERWORLD, BEACH, WATER, COLD, SNOWY, SANDY, SPARSE)),
            entry("stony_shore", traits(OVERWORLD, BEACH, WATER, TEMPERATE, SPARSE)),
            entry("warm_ocean", traits(OVERWORLD, OCEAN, WATER, HOT, WET)),
            entry("lukewarm_ocean", traits(OVERWORLD, OCEAN, WATER, TEMPERATE, WET)),
            entry("deep_lukewarm_ocean", traits(OVERWORLD, OCEAN, WATER, DEEP, TEMPERATE, WET)),
            entry("ocean", traits(OVERWORLD, OCEAN, WATER, TEMPERATE, WET)),
            entry("deep_ocean", traits(OVERWORLD, OCEAN, WATER, DEEP, TEMPERATE, WET)),
            entry("cold_ocean", traits(OVERWORLD, OCEAN, WATER, COLD, WET)),
            entry("deep_cold_ocean", traits(OVERWORLD, OCEAN, WATER, DEEP, COLD, WET)),
            entry("frozen_ocean", traits(OVERWORLD, OCEAN, WATER, COLD, WET, ICY)),
            entry("deep_frozen_ocean", traits(OVERWORLD, OCEAN, WATER, DEEP, COLD, WET, ICY)),
            entry("mushroom_fields", traits(OVERWORLD, MUSHROOM, MAGICAL, TEMPERATE, RARE, DENSE)),
            entry("dripstone_caves", traits(OVERWORLD, UNDERGROUND, CAVES, TEMPERATE, DRY)),
            entry("lush_caves", traits(OVERWORLD, UNDERGROUND, CAVES, LUSH, TEMPERATE, WET, DENSE)),
            entry("deep_dark", traits(OVERWORLD, UNDERGROUND, CAVES, SPOOKY, RARE, TEMPERATE)),
            entry("pale_garden", traits(OVERWORLD, FOREST, SPOOKY, RARE, TEMPERATE, DECIDUOUS, DENSE)),
            entry("sulfur_caves", traits(OVERWORLD, UNDERGROUND, CAVES, HOT, DRY, WASTELAND, RARE)),
            entry("nether_wastes", traits(NETHER, HOT, DRY, WASTELAND)),
            entry("crimson_forest", traits(NETHER, HOT, DRY)),
            entry("warped_forest", traits(NETHER, HOT, DRY)),
            entry("soul_sand_valley", traits(NETHER, HOT, DRY, WASTELAND, SPOOKY)),
            entry("basalt_deltas", traits(NETHER, HOT, DRY, WASTELAND)),
            entry("the_end", traits(THEEND, DRY, WASTELAND)),
            entry("end_highlands", traits(THEEND, DRY, WASTELAND)),
            entry("end_midlands", traits(THEEND, DRY, WASTELAND)),
            entry("small_end_islands", traits(THEEND, DRY, WASTELAND)),
            entry("end_barrens", traits(THEEND, DRY, WASTELAND)),
            entry("the_void", traits(VOID, DRY, WASTELAND))
    );

    @Override
    public Collection<BiomeTrait> evaluate(Identifier id, Biome biome) {
        Set<BiomeTrait> results = new HashSet<>();
        String path = id.getPath().toLowerCase(Locale.ROOT);

        if ("minecraft".equals(id.getNamespace())) {
            var vanillaTraits = VANILLA_TRAITS.get(path);
            if (vanillaTraits != null) {
                results.addAll(vanillaTraits);
            }
        }

        addNameBasedTraits(path, results);
        addClimateTraits(biome, results);
        return results;
    }

    private static void addNameBasedTraits(String path, Set<BiomeTrait> results) {
        if (path.contains("nether")) {
            results.add(NETHER);
            results.add(HOT);
            results.add(DRY);
        }

        if (path.contains("end") && !path.contains("endless")) {
            results.add(THEEND);
            results.add(DRY);
        }

        if (path.contains("cave") || path.contains("caves") || path.contains("underground")) {
            results.add(UNDERGROUND);
            results.add(CAVES);
        }

        if (path.contains("sulfur") || path.contains("sulphur")) {
            results.add(OVERWORLD);
            results.add(UNDERGROUND);
            results.add(CAVES);
            results.add(HOT);
            results.add(DRY);
            results.add(WASTELAND);
        }

        if (path.contains("ocean")) {
            results.add(OVERWORLD);
            results.add(OCEAN);
            results.add(WATER);
            if (path.contains("deep")) {
                results.add(DEEP);
            }
        }

        if (path.contains("river")) {
            results.add(OVERWORLD);
            results.add(RIVER);
            results.add(WATER);
        }

        if (path.contains("forest") || path.contains("woods") || path.contains("grove")) {
            results.add(OVERWORLD);
            results.add(FOREST);
            results.add(DENSE);
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
            results.add(WATER);
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
    }

    private static void addClimateTraits(Biome biome, Set<BiomeTrait> results) {
        float temperature = BiomeCompat.getTemperature(biome, null);
        float downfall = BiomeCompat.getDownfall(biome);

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

    private static List<BiomeTrait> traits(BiomeTrait... traits) {
        return List.of(traits);
    }
}

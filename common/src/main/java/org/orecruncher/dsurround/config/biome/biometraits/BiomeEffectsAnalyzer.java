package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;
import static org.orecruncher.dsurround.config.BiomeTrait.*;

/**
 * Traits from a biome's effects, which its author chose and the server sends the client: the vanilla music it plays,
 * its ambient loop, and its swamp or dark forest grass. Some of vanilla's music belongs to one kind of place (a
 * modded jungle plays the jungle's), and says what the biome is meant to be; the rest is played for its mood, and
 * says no more than the dimension (see {@link #SOUNDS}).
 * <p>
 * Only the kind of place, its dimension and how much grows come from here: how warm and wet it is comes from its
 * climate, which gives numbers rather than a likeness (a modded cherry grove may well be hot). And only for a biome
 * whose kind nothing more certain gave.
 */
public final class BiomeEffectsAnalyzer implements IBiomeTraitAnalyzer {

    // Vanilla's music and ambient loops, by sound event id. Mods play vanilla's music for its mood as much as its
    // place, so only that of places like no other (deserts, badlands, jungles, swamps, caves) gives a kind: Regions
    // Unexplored plays the forest's in its grasslands and fields, and the grove's and the peaks' in cold forests and
    // tundra, so those give only the Overworld. The Nether's music and loops give its kinds but not the Nether
    // itself: Allthemodium plays them in its own dimensions, and Bumblezone in its hive
    static final Map<Identifier, Set<BiomeTrait>> SOUNDS = Map.<Identifier, Set<BiomeTrait>>ofEntries(
            music("overworld.badlands", OVERWORLD, BADLANDS, WASTELAND, SANDY, SPARSE_VEGETATION),
            music("overworld.bamboo_jungle", OVERWORLD, JUNGLE, FOREST, DENSE_VEGETATION),
            music("overworld.cherry_grove", OVERWORLD),
            music("overworld.deep_dark", OVERWORLD, UNDERGROUND, CAVE, SPOOKY),
            music("overworld.desert", OVERWORLD, DESERT, SANDY, SPARSE_VEGETATION),
            music("overworld.dripstone_caves", OVERWORLD, UNDERGROUND, CAVE),
            music("overworld.flower_forest", OVERWORLD),
            music("overworld.forest", OVERWORLD),
            music("overworld.frozen_peaks", OVERWORLD),
            music("overworld.grove", OVERWORLD),
            music("overworld.jagged_peaks", OVERWORLD),
            music("overworld.jungle", OVERWORLD, JUNGLE, FOREST, DENSE_VEGETATION),
            music("overworld.lush_caves", OVERWORLD, UNDERGROUND, CAVE, LUSH, DENSE_VEGETATION),
            music("overworld.meadow", OVERWORLD),
            music("overworld.old_growth_taiga", OVERWORLD),
            music("overworld.snowy_slopes", OVERWORLD),
            music("overworld.sparse_jungle", OVERWORLD, JUNGLE, FOREST, SPARSE_VEGETATION),
            music("overworld.stony_peaks", OVERWORLD),
            music("overworld.sulfur_caves", OVERWORLD, UNDERGROUND, CAVE),
            music("overworld.swamp", OVERWORLD, SWAMP, DENSE_VEGETATION),
            music("nether.basalt_deltas", WASTELAND),
            music("nether.crimson_forest", NETHER_FOREST),
            music("nether.nether_wastes", WASTELAND),
            music("nether.soul_sand_valley", WASTELAND, SPOOKY),
            music("nether.warped_forest", NETHER_FOREST),
            music("end", END),
            loop("basalt_deltas", WASTELAND),
            loop("crimson_forest", NETHER_FOREST),
            loop("nether_wastes", WASTELAND),
            loop("soul_sand_valley", WASTELAND, SPOOKY),
            loop("warped_forest", NETHER_FOREST)
    );

    @Override
    public String name() {
        return "BiomeEffectsAnalyzer";
    }

    @Override
    public void analyze(@NotNull BiomeEvidence biome, @NotNull Set<BiomeTrait> resultCollection) {
        // Mods choose music for its mood as much as its place (Biomes O' Plenty's lavender field plays the cherry
        // grove's), so it only describes a biome nothing has said the kind of
        if (!Collections.disjoint(resultCollection, TraitGroup.KIND.members()))
            return;

        biome.music().map(SOUNDS::get).ifPresent(resultCollection::addAll);
        biome.ambientLoop().map(SOUNDS::get).ifPresent(resultCollection::addAll);

        if (biome.grassColorModifier() == BiomeSpecialEffects.GrassColorModifier.SWAMP) {
            resultCollection.add(SWAMP);
        } else if (biome.grassColorModifier() == BiomeSpecialEffects.GrassColorModifier.DARK_FOREST) {
            Collections.addAll(resultCollection, FOREST, DARK_FOREST, SPOOKY, DENSE_VEGETATION);
        }
    }

    private static Map.Entry<Identifier, Set<BiomeTrait>> music(String name, BiomeTrait... traits) {
        return entry(Identifier.withDefaultNamespace("music." + name), traits(traits));
    }

    private static Map.Entry<Identifier, Set<BiomeTrait>> loop(String name, BiomeTrait... traits) {
        return entry(Identifier.withDefaultNamespace("ambient." + name + ".loop"), traits(traits));
    }

    private static Set<BiomeTrait> traits(BiomeTrait... traits) {
        var result = EnumSet.noneOf(BiomeTrait.class);
        Collections.addAll(result, traits);
        return result;
    }
}

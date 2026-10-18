package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.orecruncher.dsurround.config.BiomeTrait.*;

public class BiomeTraitAnalysisTests {

    static {
        // BiomeTrait reaches the game's registries; first, before the fields below use it
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final Set<BiomeTrait> DIMENSIONS = Set.of(OVERWORLD, NETHER, END, VOID);

    // Pairs that can't both describe one biome; cleanup removes one of each
    private static final List<Set<BiomeTrait>> CONFLICTS = List.of(
            Set.of(OVERWORLD, NETHER), Set.of(OVERWORLD, END), Set.of(NETHER, END),
            Set.of(WET, DRY), Set.of(COLD, HOT), Set.of(DENSE_VEGETATION, SPARSE_VEGETATION));

    private static Set<BiomeTrait> fromName(String path) {
        var traits = EnumSet.noneOf(BiomeTrait.class);
        BiomeNameFallbackAnalyzer.addNameBasedTraits(path, traits);
        return traits;
    }

    /**
     * Runs the analyzers that follow the tag and name analysis, in their order. They don't look at the biome itself.
     */
    private static Set<BiomeTrait> derive(BiomeTrait... traits) {
        var set = EnumSet.noneOf(BiomeTrait.class);
        set.addAll(List.of(traits));
        var biome = evidence(ResourceLocation.fromNamespaceAndPath("test", "biome"));
        new BiomeTraitAnalyzer().analyze(biome, set);
        new BiomeTraitCleanup().analyze(biome, set);
        return set;
    }

    private static BiomeEvidence evidence(ResourceLocation id) {
        return new BiomeEvidence(id, null, true, 0.5F, false, 0.5F, BiomeSpecialEffects.GrassColorModifier.NONE,
                Optional.empty(), Optional.empty());
    }

    private static Set<String> vanillaBiomeNames() throws IllegalAccessException {
        var names = new TreeSet<String>();
        for (var field : Biomes.class.getFields())
            if (Modifier.isStatic(field.getModifiers()) && field.get(null) instanceof ResourceKey<?> key)
                names.add(key.location().getPath());
        return names;
    }

    // ---- The vanilla table

    @Test
    void everyVanillaBiomeIsInTheTable() throws IllegalAccessException {
        var names = vanillaBiomeNames();
        assertTrue(names.size() > 50, "didn't find the vanilla biomes");
        var missing = new TreeSet<>(names);
        missing.removeAll(VanillaBiomeAnalyzer.VANILLA_TRAITS.keySet());
        assertTrue(missing.isEmpty(), "vanilla biomes without traits: " + missing);
    }

    @Test
    void eachVanillaBiomeIsInOneDimension() {
        VanillaBiomeAnalyzer.VANILLA_TRAITS.forEach((name, traits) -> {
            var dims = EnumSet.noneOf(BiomeTrait.class);
            dims.addAll(traits);
            dims.retainAll(DIMENSIONS);
            assertEquals(1, dims.size(), name + " is in dimensions " + dims);
        });
    }

    @Test
    void eachVanillaBiomeHasAKind() {
        // So a vanilla server, sending none of the c: tags, leaves none of them in the dump's biomes to check
        VanillaBiomeAnalyzer.VANILLA_TRAITS.forEach((name, traits) ->
                assertFalse(Collections.disjoint(traits, TraitGroup.KIND.members()), name + " has no kind"));
    }

    @Test
    void noVanillaBiomeHasContradictoryTraits() {
        // Cleanup would quietly drop one of each pair, hiding a mistake in the table
        VanillaBiomeAnalyzer.VANILLA_TRAITS.forEach((name, traits) -> {
            for (var pair : CONFLICTS)
                assertFalse(traits.containsAll(pair), name + " has both of " + pair);
        });
    }

    @Test
    void vanillaBiomesUseTheTable() {
        var traits = EnumSet.noneOf(BiomeTrait.class);
        // The biome isn't looked at for a vanilla biome in the table
        new VanillaBiomeAnalyzer().analyze(evidence(ResourceLocation.withDefaultNamespace("snowy_plains")), traits);
        assertEquals(VanillaBiomeAnalyzer.VANILLA_TRAITS.get("snowy_plains"), traits);
    }

    @Test
    void coldVanillaBiomesAreCold() {
        // The aurora shows over these
        for (var name : List.of("snowy_plains", "ice_spikes", "snowy_taiga", "grove", "snowy_slopes", "frozen_peaks",
                "jagged_peaks", "frozen_river", "snowy_beach", "frozen_ocean", "deep_frozen_ocean"))
            assertTrue(VanillaBiomeAnalyzer.VANILLA_TRAITS.get(name).contains(COLD), name + " isn't cold");
    }

    // ---- Names

    @Test
    void endIsMatchedAsAWholeWord() {
        // Regression: Biomes O' Plenty's lavender_field was made an End biome, and cleanup then took away OVERWORLD
        assertFalse(fromName("lavender_field").contains(END));
        assertFalse(fromName("endless_meadow").contains(END));
        for (var name : List.of("end_reef", "end_wilds", "end_corruption", "the_end", "crystal_end"))
            assertTrue(fromName(name).contains(END), name);
    }

    @Test
    void hasExactWordSplitsOnUnderscoresAndSlashes() {
        assertTrue(BiomeNameFallbackAnalyzer.hasExactWord("end", "end"));
        assertTrue(BiomeNameFallbackAnalyzer.hasExactWord("islands/end/outer", "end"));
        assertFalse(BiomeNameFallbackAnalyzer.hasExactWord("lavender_field", "end"));
        assertFalse(BiomeNameFallbackAnalyzer.hasExactWord("ending", "end"));
    }

    @Test
    void wordsMayHaveCommonEndings() {
        assertTrue(BiomeNameFallbackAnalyzer.hasWord("snowy_hills", "snow"));
        assertTrue(BiomeNameFallbackAnalyzer.hasWord("crystal_caves", "cave"));
        assertTrue(BiomeNameFallbackAnalyzer.hasWord("pinelands", "pine"));
        assertTrue(BiomeNameFallbackAnalyzer.hasWord("pinewood_grove", "pine"));
        assertTrue(BiomeNameFallbackAnalyzer.hasWord("salt_marshes", "marsh"));
    }

    @Test
    void wordsArentFoundInsideOtherWords() {
        // Regression: names were searched for the letters anywhere
        assertFalse(fromName("alpine_meadow").contains(CONIFEROUS), "alpine");
        assertFalse(fromName("spine_ridge").contains(CONIFEROUS), "spine");
        assertFalse(fromName("dice_fields").contains(SNOWY), "dice");
        assertFalse(fromName("spice_grove").contains(COLD), "spice");
        assertFalse(fromName("palette_hills").contains(SPOOKY), "palette");
        assertFalse(fromName("rice_paddies").contains(COLD), "rice");
    }

    @Test
    void namesSuggestTraits() {
        assertTrue(fromName("nether_garden").containsAll(Set.of(NETHER, HOT, DRY)));
        assertTrue(fromName("deep_warm_ocean").containsAll(Set.of(OCEAN, DEEP_OCEAN, WET)));
        assertTrue(fromName("crystal_caves").containsAll(Set.of(UNDERGROUND, CAVE)));
        assertTrue(fromName("snowy_coniferous_forest").containsAll(Set.of(COLD, SNOWY, FOREST, CONIFEROUS, TAIGA)));
        assertTrue(fromName("redwood_forest").containsAll(Set.of(FOREST, DENSE_VEGETATION)));
        assertTrue(fromName("bog").containsAll(Set.of(SWAMP, WET)));
        assertTrue(fromName("rocky_shore").containsAll(Set.of(BEACH, WET)));
        assertTrue(fromName("sulfur_springs").containsAll(Set.of(UNDERGROUND, CAVE, HOT, DRY)));
        assertTrue(fromName("mystic_grove").containsAll(Set.of(FOREST)));
        assertEquals(Set.of(PLAINS), fromName("lavender_field"), "a field, and not the End");
    }

    @Test
    void namesSayPlainsSeasRiversAndMore() {
        for (var name : List.of("barley_fields", "forgotten_field", "clover_plains", "dread_plain", "prairie", "grassland",
                "rocky_meadow", "steppe", "clearing", "shrubland"))
            assertTrue(fromName(name).contains(PLAINS), name);
        for (var name : List.of("ancient_sea", "starlit_sea", "lush_shallow_sea"))
            assertTrue(fromName(name).contains(OCEAN), name);
        for (var name : List.of("stream", "lake"))
            assertTrue(fromName(name).contains(RIVER), name);
        assertTrue(fromName("oak_savannah").contains(SAVANNA));
        assertTrue(fromName("final_plateau").contains(PLATEAU));
        assertTrue(fromName("highlands").contains(HILL));
        assertTrue(fromName("frozen_tundra").containsAll(Set.of(PLAINS, SNOWY)));
        assertTrue(fromName("glacier").containsAll(Set.of(SNOWY, ICY)));
    }

    @Test
    void newWordsArentFoundInsideOthers() {
        assertFalse(fromName("grassy_beach").contains(PLAINS), "grassy");
        assertFalse(fromName("seasonal_forest").contains(OCEAN), "seasonal");
        assertFalse(fromName("fieldstone_ridge").contains(PLAINS), "fieldstone");
    }

    @Test
    void namesDontSayTheOverworld() {
        // Regression: the Twilight Forest's and the Undergarden's forests, rivers and shores were made Overworld
        for (var name : List.of("dense_forest", "ether_river", "warm_shore", "deep_ocean", "sulfur_caves"))
            assertFalse(fromName(name).contains(OVERWORLD), name);
    }

    // ---- Climate

    @Test
    void temperatureGivesColdTemperateOrHot() {
        assertEquals(Set.of(COLD), climate(0.0F, 0.5F));
        assertEquals(Set.of(COLD), climate(0.15F, 0.5F));
        assertEquals(Set.of(TEMPERATE), climate(0.5F, 0.5F));
        assertEquals(Set.of(HOT), climate(0.95F, 0.5F));
        assertEquals(Set.of(HOT), climate(2.0F, 0.5F));
    }

    @Test
    void downfallGivesDryOrWet() {
        assertTrue(climate(0.5F, 0.0F).contains(DRY));
        assertTrue(climate(0.5F, 0.15F).contains(DRY));
        assertTrue(climate(0.5F, 0.7F).contains(WET));
        var between = climate(0.5F, 0.4F);
        assertFalse(between.contains(DRY) || between.contains(WET));
    }

    private static Set<BiomeTrait> climate(float temperature, float downfall) {
        var traits = EnumSet.noneOf(BiomeTrait.class);
        BiomeClimateAnalyzer.addClimateTraits(true, temperature, false, downfall, traits);
        return traits;
    }

    // ---- Derived traits and cleanup

    @Test
    void derivesTraitsFromOthers() {
        assertTrue(derive(CAVE).contains(UNDERGROUND));
        assertTrue(derive(SNOWY).contains(COLD));
        assertTrue(derive(ICY).contains(COLD));
        assertTrue(derive(SNOWY_PLAINS).contains(COLD));
        assertTrue(derive(RIVER).contains(AQUATIC));
        assertTrue(derive(DEEP_OCEAN).contains(AQUATIC));
        assertTrue(derive(RIVER, ICY).contains(AQUATIC_ICY));
        assertFalse(derive(ICY).contains(AQUATIC_ICY));
    }

    @Test
    void cleanupResolvesConflicts() {
        assertEquals(Set.of(NETHER), derive(NETHER, OVERWORLD, END));
        assertEquals(Set.of(END), derive(END, OVERWORLD));
        assertEquals(Set.of(DENSE_VEGETATION), derive(DENSE_VEGETATION, SPARSE_VEGETATION));
        assertEquals(Set.of(WET), derive(WET, DRY));
        assertEquals(Set.of(TEMPERATE), derive(TEMPERATE, COLD, HOT));
        assertEquals(Set.of(HOT), derive(HOT, COLD));
        assertFalse(derive(RIVER, OCEAN, DEEP_OCEAN, SHALLOW_OCEAN).stream().anyMatch(t -> t == OCEAN || t == DEEP_OCEAN || t == SHALLOW_OCEAN));
        assertFalse(derive(DEEP_OCEAN, OCEAN, SHALLOW_OCEAN).contains(OCEAN));
        assertFalse(derive(SHALLOW_OCEAN, OCEAN).contains(OCEAN));
    }

    @Test
    void cleanedUpVanillaBiomesHaveNoConflicts() {
        VanillaBiomeAnalyzer.VANILLA_TRAITS.forEach((name, traits) -> {
            var result = derive(traits.toArray(new BiomeTrait[0]));
            for (var pair : CONFLICTS)
                assertFalse(result.containsAll(pair), name + " ends up with both of " + pair);
        });
    }

    @Test
    void frozenRiverEndsUpColdAndIcyWater() {
        var traits = derive(VanillaBiomeAnalyzer.VANILLA_TRAITS.get("frozen_river").toArray(new BiomeTrait[0]));
        assertTrue(traits.containsAll(Set.of(OVERWORLD, RIVER, AQUATIC, AQUATIC_ICY, COLD, ICY)));
    }
}

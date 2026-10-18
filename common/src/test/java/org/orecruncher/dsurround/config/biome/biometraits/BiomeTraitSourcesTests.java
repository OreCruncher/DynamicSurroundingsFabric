package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.BiomeSpecialEffects.GrassColorModifier;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.orecruncher.dsurround.config.BiomeTrait.*;

/**
 * Tests for working out a biome's traits from all its sources: which wins, what a guess may add, and how the dump
 * shows where each came from.
 */
public class BiomeTraitSourcesTests {

    static {
        // BiomeTrait and the sound registry reach the game's registries
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * A biome in test:{@code path}: temperate, some rain, no effects; change what a test needs with the withers
     */
    private record Biome(String namespace, String path, boolean rain, float temperature, boolean frozen, float downfall,
                         GrassColorModifier grass, String music, String loop) {
        static Biome named(String path) {
            return new Biome("test", path, true, 0.5F, false, 0.5F, GrassColorModifier.NONE, null, null);
        }

        static Biome vanilla(String path) {
            return new Biome("minecraft", path, true, 0.5F, false, 0.5F, GrassColorModifier.NONE, null, null);
        }

        Biome temperature(float t) {
            return new Biome(namespace, path, rain, t, frozen, downfall, grass, music, loop);
        }

        Biome downfall(float d) {
            return new Biome(namespace, path, rain, temperature, frozen, d, grass, music, loop);
        }

        Biome noRain() {
            return new Biome(namespace, path, false, temperature, frozen, downfall, grass, music, loop);
        }

        Biome frozenOver() {
            return new Biome(namespace, path, rain, temperature, true, downfall, grass, music, loop);
        }

        Biome grass(GrassColorModifier g) {
            return new Biome(namespace, path, rain, temperature, frozen, downfall, g, music, loop);
        }

        Biome music(String m) {
            return new Biome(namespace, path, rain, temperature, frozen, downfall, grass, m, loop);
        }

        Biome loop(String l) {
            return new Biome(namespace, path, rain, temperature, frozen, downfall, grass, music, l);
        }

        BiomeEvidence evidence() {
            return new BiomeEvidence(ResourceLocation.fromNamespaceAndPath(namespace, path), null,
                    rain, temperature, frozen, downfall, grass,
                    Optional.ofNullable(music).map(ResourceLocation::parse), Optional.ofNullable(loop).map(ResourceLocation::parse));
        }

        BiomeTraits traits(BiomeTrait... tags) {
            return BiomeTraits.from(this.evidence(), tags(tags));
        }
    }

    private static IBiomeTraitAnalyzer tags(BiomeTrait... traits) {
        return new IBiomeTraitAnalyzer() {
            @Override
            public String name() {
                return "tags";
            }

            @Override
            public void analyze(@NotNull BiomeEvidence biome, @NotNull Set<BiomeTrait> resultCollection) {
                Collections.addAll(resultCollection, traits);
            }
        };
    }

    // ---- Which source wins

    @Test
    void aTaggedTemperatureIsNotReplacedByTheClimate() {
        // Regression: a modded taiga tagged cold, at vanilla taiga's 0.25, was made temperate by its climate and
        // cleanup then took the cold away
        var traits = Biome.named("pine_taiga").temperature(0.25F).traits(COLD, FOREST, TAIGA);
        assertTrue(traits.contains(COLD));
        assertFalse(traits.contains(TEMPERATE));
        assertEquals(TraitSource.TAG, traits.sourceOf(COLD));
    }

    @Test
    void aTaggedMoistureIsNotReplacedByTheName() {
        var traits = Biome.named("dry_swamp").downfall(0.1F).traits(DRY);
        assertTrue(traits.contains(DRY));
        assertFalse(traits.contains(WET));
    }

    @Test
    void whatTagsImplyComesBeforeTheGuesses() {
        // Tagged snowy, which is cold; its temperature isn't left to the climate
        var traits = Biome.named("somewhere").temperature(0.5F).traits(SNOWY);
        assertTrue(traits.contains(COLD));
        assertFalse(traits.contains(TEMPERATE));
        assertEquals(TraitSource.DERIVED, traits.sourceOf(COLD));
    }

    @Test
    void aGuessFillsAGroupNothingElseDid() {
        var traits = Biome.named("somewhere").temperature(0.05F).traits(FOREST);
        assertEquals(TraitSource.CLIMATE, traits.sourceOf(COLD));
        assertEquals(Map.of(TraitGroup.TEMPERATURE, Set.of(COLD)), traits.guessedGroups());
    }

    @Test
    void guessesStillAddTraitsOutsideTheGroups() {
        // Tagged a forest, its name adds that it is coniferous
        var traits = Biome.named("pine_woods").traits(FOREST);
        assertTrue(traits.contains(CONIFEROUS));
        assertEquals(TraitSource.NAME, traits.sourceOf(CONIFEROUS));
    }

    @Test
    void theEffectsOutrankTheName() {
        // Overworld music: the dimension is the Overworld, whatever the name says
        var traits = Biome.named("end_dunes").music("minecraft:music.overworld.desert").traits();
        assertTrue(traits.contains(OVERWORLD));
        assertFalse(traits.contains(END));
        assertEquals(TraitSource.EFFECTS, traits.sourceOf(OVERWORLD));
    }

    @Test
    void musicDoesntRedescribeATaggedBiome() {
        // Regression: Biomes O' Plenty's lavender field, tagged plains, plays the cherry grove's music and became a
        // deciduous forest on a mountain
        var traits = Biome.named("lavender_field").music("minecraft:music.overworld.jungle").traits(OVERWORLD, PLAINS);
        for (var trait : List.of(FOREST, JUNGLE, DENSE_VEGETATION))
            assertFalse(traits.contains(trait), trait + " in " + traits.describe());
    }

    @Test
    void aNameDoesntAddKindsToATaggedBiome() {
        var traits = Biome.named("pine_forest").traits(PLAINS);
        assertFalse(traits.contains(FOREST));
        assertFalse(traits.contains(TAIGA));
        assertTrue(traits.contains(CONIFEROUS), "describing it still counts");
    }

    @Test
    void oneGuessCanGiveSeveralKinds() {
        var traits = Biome.named("snowy_mountain_forest").temperature(0.0F).traits();
        assertHas(traits, FOREST, MOUNTAIN);
        assertEquals(Set.of(FOREST, MOUNTAIN), traits.guessedGroups().get(TraitGroup.KIND));
    }

    @Test
    void theMusicsKindOutranksTheName() {
        var traits = Biome.named("ash_ocean").music("minecraft:music.nether.basalt_deltas").traits();
        assertFalse(traits.contains(OCEAN));
        assertEquals(TraitSource.EFFECTS, traits.sourceOf(WASTELAND));
    }

    @Test
    void theClimateOutranksTheName() {
        // Named frozen, but its numbers say otherwise
        var traits = Biome.named("frozen_garden").temperature(0.8F).traits();
        assertTrue(traits.contains(TEMPERATE));
        assertFalse(traits.contains(COLD));
        assertTrue(traits.contains(SNOWY), "the name's snowy isn't in a group");
    }

    @Test
    void vanillaBiomesUseTheTableAndNoGuesses() {
        // Plains in the table are temperate, whatever the evidence says
        var traits = Biome.vanilla("plains").temperature(2.0F).noRain().music("minecraft:music.nether.nether_wastes").traits();
        assertEquals(VanillaBiomeAnalyzer.VANILLA_TRAITS.get("plains"), Set.copyOf(collect(traits)));
        assertEquals(TraitSource.VANILLA, traits.sourceOf(PLAINS));
        assertTrue(traits.guessedGroups().isEmpty());
    }

    @Test
    void theTableSettlesAVanillaBiomesGroups() {
        // The convention tags make the windswept hills cold and the mushroom fields hot; the table, as a vanilla
        // server (sending only Minecraft's tags) would have them, says temperate
        var hills = Biome.vanilla("windswept_hills").traits(COLD);
        assertTrue(hills.contains(TEMPERATE));
        assertFalse(hills.contains(COLD));
        assertFalse(Biome.vanilla("mushroom_fields").traits(HOT).contains(HOT));
        assertFalse(Biome.vanilla("dripstone_caves").traits(DRY).contains(DRY));
    }

    @Test
    void tagsStillAddToAVanillaBiome() {
        // Kinds the table leaves out, and groups it says nothing of (the End's temperature)
        assertTrue(Biome.vanilla("cherry_grove").traits(PLATEAU).contains(PLATEAU));
        var end = Biome.vanilla("the_end").traits(COLD);
        assertTrue(end.contains(COLD));
        assertEquals(TraitSource.TAG, end.sourceOf(COLD));
    }

    @Test
    void anUnknownMinecraftBiomeIsGuessed() {
        var traits = Biome.vanilla("some_future_desert").temperature(2.0F).noRain().traits();
        assertTrue(traits.contains(DESERT));
        assertEquals(TraitSource.CLIMATE, traits.sourceOf(HOT));
    }

    // ---- Effects

    @Test
    void vanillaMusicSaysWhatKindOfPlaceItIs() {
        var traits = Biome.named("somewhere").music("minecraft:music.overworld.jungle").traits();
        assertHas(traits, OVERWORLD, JUNGLE, FOREST, DENSE_VEGETATION);
        assertEquals(TraitSource.EFFECTS, traits.sourceOf(JUNGLE));
    }

    @Test
    void moodMusicSaysOnlyTheOverworld() {
        // Regression: Regions Unexplored's grasslands play the forest's music and became dense forests, its tundra
        // the grove's and became a mountain forest; Biomes O' Plenty's snowblossom grove the cherry grove's
        for (var music : List.of("forest", "flower_forest", "cherry_grove", "grove", "meadow", "old_growth_taiga",
                "frozen_peaks", "jagged_peaks", "snowy_slopes", "stony_peaks")) {
            var traits = Biome.named("somewhere").music("minecraft:music.overworld." + music).traits();
            assertEquals(Set.of(OVERWORLD), effects(traits), music);
        }
    }

    @Test
    void netherSoundsDontSayTheNether() {
        // Allthemodium plays them in its own dimensions, Bumblezone in its hive
        var traits = Biome.named("somewhere").music("minecraft:music.nether.basalt_deltas").traits();
        assertFalse(traits.contains(NETHER));
        assertTrue(traits.contains(WASTELAND));
    }

    @Test
    void musicDoesntSetTheTemperature() {
        // A cherry grove can be anywhere: its temperature comes from its climate
        var traits = Biome.named("somewhere").music("minecraft:music.overworld.cherry_grove").temperature(1.5F).traits();
        assertEquals(TraitSource.CLIMATE, traits.sourceOf(HOT));
    }

    @Test
    void modMusicSaysNothing() {
        var traits = Biome.named("somewhere").music("somemod:music.overworld.jungle").traits();
        assertFalse(traits.contains(JUNGLE));
    }

    @Test
    void netherLoopsGiveTheirKind() {
        var traits = Biome.named("somewhere").loop("minecraft:ambient.crimson_forest.loop").traits();
        assertTrue(traits.contains(NETHER_FOREST));
        assertFalse(traits.contains(NETHER));
    }

    @Test
    void grassModifiersSaySwampOrDarkForest() {
        assertTrue(Biome.named("somewhere").grass(GrassColorModifier.SWAMP).traits().contains(SWAMP));
        var dark = Biome.named("somewhere").grass(GrassColorModifier.DARK_FOREST).traits();
        assertHas(dark, FOREST, DARK_FOREST, SPOOKY);
    }

    @Test
    void everySoundInTheTableIsVanillas() {
        for (var id : BiomeEffectsAnalyzer.SOUNDS.keySet())
            assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(id), id + " isn't a vanilla sound event");
    }

    @Test
    void noSoundGivesTwoDimensions() {
        BiomeEffectsAnalyzer.SOUNDS.forEach((id, traits) -> {
            var dims = EnumSet.copyOf(traits);
            dims.retainAll(TraitGroup.DIMENSION.members());
            assertTrue(dims.size() <= 1, id + " gives dimensions " + dims);
            assertFalse(dims.contains(NETHER), id + " gives the Nether");
            for (var group : List.of(TraitGroup.TEMPERATURE, TraitGroup.MOISTURE))
                assertTrue(Collections.disjoint(traits, group.members()), id + " sets " + group);
        });
    }

    // ---- Climate

    @Test
    void noRainIsDry() {
        assertTrue(Biome.named("somewhere").noRain().downfall(0.5F).traits().contains(DRY));
    }

    @Test
    void waterThatFreezesOverIsIcy() {
        assertTrue(Biome.named("somewhere").frozenOver().traits().contains(ICY));
    }

    // ---- How they are shown

    @Test
    void describeListsTraitsByWhereTheyCameFrom() {
        var traits = Biome.named("somewhere").traits(FOREST);
        assertEquals("FOREST (tags); TEMPERATE (climate)", traits.describe());
    }

    @Test
    void toStringIsUnchangedForScripts() {
        // Scripts read biome.getTraits() as this string
        assertEquals("[FOREST, TEMPERATE]", Biome.named("somewhere").traits(FOREST).toString());
        var traits = BiomeTraits.of(FOREST);
        traits.merge(List.of(RARE));
        assertEquals("*[FOREST, RARE]", traits.toString());
        assertEquals(TraitSource.CONFIG, traits.sourceOf(RARE));
    }

    @Test
    void groupsGivenByTagsArentGuessed() {
        var traits = Biome.named("somewhere").temperature(0.05F).downfall(0.9F).traits(OVERWORLD, COLD);
        assertEquals(Map.of(TraitGroup.MOISTURE, Set.of(WET)), traits.guessedGroups());
    }

    @Test
    void theClimatesOwnNumbersArentDoubted() {
        var traits = Biome.named("somewhere").temperature(0.05F).downfall(0.9F).traits(OVERWORLD, FOREST);
        assertEquals(Set.of(TraitGroup.TEMPERATURE, TraitGroup.MOISTURE), traits.guessedGroups().keySet());
        assertTrue(traits.doubtfulGroups().isEmpty(), traits.doubtfulGroups().toString());
    }

    @Test
    void aMoistureFromTheNameIsDoubted() {
        // A beach is wet by its name, its downfall saying neither
        var traits = Biome.named("gravel_beach").downfall(0.5F).traits(OVERWORLD);
        assertEquals(Map.of(TraitGroup.MOISTURE, Set.of(WET), TraitGroup.KIND, Set.of(BEACH)), traits.doubtfulGroups());
    }

    @Test
    void kindAndVegetationFromTheMusicAreDoubted() {
        var traits = Biome.named("somewhere").music("minecraft:music.overworld.swamp").traits(OVERWORLD);
        assertEquals(Set.of(SWAMP), traits.doubtfulGroups().get(TraitGroup.KIND));
        assertEquals(Set.of(DENSE_VEGETATION), traits.doubtfulGroups().get(TraitGroup.VEGETATION));
    }

    @Test
    void aBiomeWithNoDimensionOrKindIsMissingThem() {
        assertEquals(Set.of(TraitGroup.DIMENSION, TraitGroup.KIND), Biome.named("somewhere").traits().missingGroups());
        // Biomes O' Plenty's End biomes are tagged End, cold and dry, and nothing gives them a kind
        assertEquals(Set.of(TraitGroup.KIND), Biome.named("end_reef").temperature(0.5F).traits(END, COLD, DRY).missingGroups());
        assertTrue(Biome.named("somewhere").traits(OVERWORLD, FOREST).missingGroups().isEmpty());
    }

    // ---- Helpers

    /**
     * The traits that came from the effects
     */
    private static Set<BiomeTrait> effects(BiomeTraits traits) {
        var result = EnumSet.noneOf(BiomeTrait.class);
        traits.forEach(t -> {
            if (traits.sourceOf(t) == TraitSource.EFFECTS)
                result.add(t);
        });
        return result;
    }

    private static void assertHas(BiomeTraits traits, BiomeTrait... expected) {
        for (var trait : expected)
            assertTrue(traits.contains(trait), "missing " + trait + " in " + traits.describe());
    }

    private static List<BiomeTrait> collect(BiomeTraits traits) {
        var list = new ArrayList<BiomeTrait>();
        traits.forEach(list::add);
        return list;
    }
}

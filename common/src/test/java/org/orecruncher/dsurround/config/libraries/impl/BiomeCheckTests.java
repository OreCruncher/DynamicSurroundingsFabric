package org.orecruncher.dsurround.config.libraries.impl;

import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.orecruncher.dsurround.config.BiomeTrait.*;

/**
 * Tests for what the biome dump asks to have checked, with and without the world's generation to go by.
 */
public class BiomeCheckTests {

    static {
        // BiomeTrait reaches the game's registries
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final Identifier OVERWORLD_DIM = Identifier.withDefaultNamespace("overworld");
    private static final Identifier NETHER_DIM = Identifier.withDefaultNamespace("the_nether");
    private static final Identifier TWILIGHT = Identifier.fromNamespaceAndPath("twilightforest", "twilight_forest");

    // ---- With the world's generation (single player)

    @Test
    void aDimensionThatMatchesWhereItGeneratesIsFine() {
        assertEquals(List.of(), BiomeCheck.notes(BiomeTraits.of(OVERWORLD, FOREST), Set.of(OVERWORLD_DIM), false));
        // In the Overworld and a modded dimension: the Overworld's trait fits
        assertEquals(List.of(), BiomeCheck.notes(BiomeTraits.of(OVERWORLD, FOREST), Set.of(OVERWORLD_DIM, TWILIGHT), false));
    }

    @Test
    void aDimensionItDoesntGenerateInIsNoted() {
        assertEquals(List.of("dimension OVERWORLD (config), but generates in twilightforest:twilight_forest"),
                BiomeCheck.notes(BiomeTraits.of(OVERWORLD, FOREST), Set.of(TWILIGHT), false));
        assertEquals(List.of("dimension NETHER (config), but generates in minecraft:overworld"),
                BiomeCheck.notes(BiomeTraits.of(NETHER, WASTELAND), Set.of(OVERWORLD_DIM), false));
    }

    @Test
    void noDimensionWhereOneIsDueIsNoted() {
        assertEquals(List.of("no dimension, but generates in minecraft:the_nether"),
                BiomeCheck.notes(BiomeTraits.of(WASTELAND), Set.of(NETHER_DIM), false));
    }

    @Test
    void noDimensionInAModdedDimensionIsRight() {
        // None of the dimension traits describes the Twilight Forest
        assertEquals(List.of(), BiomeCheck.notes(BiomeTraits.of(FOREST), Set.of(TWILIGHT), false));
    }

    @Test
    void noKindIsStillNoted() {
        assertEquals(List.of("no kind"), BiomeCheck.notes(BiomeTraits.of(OVERWORLD), Set.of(OVERWORLD_DIM), false));
    }

    @Test
    void noKindInOnlyModdedDimensionsIsRight() {
        // A dimension's only biome, as Allthemodium's mining dimension or the Bumblezone's hive: no kind describes it
        assertEquals(List.of(), BiomeCheck.notes(BiomeTraits.of(WET), Set.of(TWILIGHT), false));
        // Also in the Overworld, it is noted
        assertEquals(List.of("no dimension, but generates in minecraft:overworld, twilightforest:twilight_forest", "no kind"),
                BiomeCheck.notes(BiomeTraits.of(WET), Set.of(OVERWORLD_DIM, TWILIGHT), false));
    }

    @Test
    void aVoidIsntChecked() {
        // Vanilla's void, used by Iron's Spellbooks for its pocket dimension
        var pocket = Identifier.fromNamespaceAndPath("irons_spellbooks", "pocket_dimension");
        assertEquals(List.of(), BiomeCheck.notes(BiomeTraits.of(VOID, WASTELAND), Set.of(pocket), false));
    }

    // ---- Without it (multiplayer)

    @Test
    void withoutGenerationNoDimensionIsNotedUnlessTheModHasItsOwn() {
        assertEquals(List.of("no dimension"), BiomeCheck.notes(BiomeTraits.of(FOREST), null, false));
        assertEquals(List.of(), BiomeCheck.notes(BiomeTraits.of(FOREST), null, true));
    }

    @Test
    void withoutGenerationNoKindIsNotedUnlessTheModHasItsOwnDimensions() {
        assertEquals(List.of("no kind"), BiomeCheck.notes(BiomeTraits.of(OVERWORLD), null, false));
        assertEquals(List.of(), BiomeCheck.notes(BiomeTraits.of(OVERWORLD), null, true));
    }

    @Test
    void withoutGenerationADimensionIsntChecked() {
        assertEquals(List.of(), BiomeCheck.notes(BiomeTraits.of(OVERWORLD, FOREST), null, true));
    }
}

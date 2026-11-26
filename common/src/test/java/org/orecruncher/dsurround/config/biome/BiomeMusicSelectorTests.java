// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.biome;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.orecruncher.dsurround.config.biome.BiomeInfoMusicTests.*;

/**
 * Choosing the background music: the choice is kept until asked to choose again or the choices change.
 */
public class BiomeMusicSelectorTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final int DRAWS = 200;

    @Test
    void noMusicAnywhereGivesNone() {
        assertTrue(new BiomeMusicSelector().select(info(), Randomizer.create(42), true).isEmpty());
    }

    @Test
    void theChoiceIsKeptUntilAskedToChooseAgain() {
        var info = info(musicRule(false, "one", "two", "three"), gameMusic("vanilla"));
        var selector = new BiomeMusicSelector();
        IRandomizer random = Randomizer.create(42);
        var first = selector.select(info, random, true);
        assertTrue(first.isPresent());
        for (int i = 0; i < DRAWS; i++)
            assertEquals(first, selector.select(info, random, false));
    }

    @Test
    void askingToChooseAgainMakesANewChoice() {
        var info = info(musicRule(false, "one", "two", "three"), gameMusic("vanilla"));
        var selector = new BiomeMusicSelector();
        IRandomizer random = Randomizer.create(42);
        var first = selector.select(info, random, true);
        var changed = false;
        for (int i = 0; i < DRAWS && !changed; i++)
            changed = !first.equals(selector.select(info, random, true));
        assertTrue(changed);
    }

    @Test
    void aChoiceIsMadeWhenNoneIsKept() {
        assertEquals(Optional.of(id("vanilla")),
                new BiomeMusicSelector().select(info(gameMusic("vanilla")), Randomizer.create(42), false)
                        .map(BiomeInfoMusicTests::location));
    }

    @Test
    void aDifferentBiomeMakesANewChoice() {
        var selector = new BiomeMusicSelector();
        IRandomizer random = Randomizer.create(42);
        var one = info(musicRule(false, "one"));
        var two = info(musicRule(false, "two"));
        assertEquals(Optional.of(id("one")), selector.select(one, random, true).map(BiomeInfoMusicTests::location));
        assertEquals(Optional.of(id("two")), selector.select(two, random, false).map(BiomeInfoMusicTests::location));
    }
}

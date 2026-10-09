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
        assertTrue(new BiomeMusicSelector().select(info(), Optional.empty(), Randomizer.create(42), true).isEmpty());
    }

    @Test
    void theChoiceIsKeptUntilAskedToChooseAgain() {
        var info = info();
        info.update(musicRule(false, "one", "two", "three"));
        var vanilla = Optional.of(music(id("vanilla")));
        var selector = new BiomeMusicSelector();
        IRandomizer random = Randomizer.create(42);
        var first = selector.select(info, vanilla, random, true);
        assertTrue(first.isPresent());
        for (int i = 0; i < DRAWS; i++)
            assertEquals(first, selector.select(info, vanilla, random, false));
    }

    @Test
    void askingToChooseAgainMakesANewChoice() {
        var info = info();
        info.update(musicRule(false, "one", "two", "three"));
        var vanilla = Optional.of(music(id("vanilla")));
        var selector = new BiomeMusicSelector();
        IRandomizer random = Randomizer.create(42);
        var first = selector.select(info, vanilla, random, true);
        var changed = false;
        for (int i = 0; i < DRAWS && !changed; i++)
            changed = !first.equals(selector.select(info, vanilla, random, true));
        assertTrue(changed);
    }

    @Test
    void aChoiceIsMadeWhenNoneIsKept() {
        assertEquals(Optional.of(id("vanilla")),
                new BiomeMusicSelector().select(info(), Optional.of(music(id("vanilla"))), Randomizer.create(42), false)
                        .map(BiomeInfoMusicTests::location));
    }

    @Test
    void aChangeInTheGameTrackMakesANewChoice() {
        var info = info();
        var selector = new BiomeMusicSelector();
        IRandomizer random = Randomizer.create(42);
        selector.select(info, Optional.of(music(id("first"))), random, true);
        assertEquals(Optional.of(id("second")),
                selector.select(info, Optional.of(music(id("second"))), random, false).map(BiomeInfoMusicTests::location));
    }

    @Test
    void aDifferentBiomeMakesANewChoice() {
        var selector = new BiomeMusicSelector();
        IRandomizer random = Randomizer.create(42);
        var one = info();
        one.update(musicRule(false, "one"));
        var two = info();
        two.update(musicRule(false, "two"));
        assertEquals(Optional.of(id("one")), selector.select(one, Optional.empty(), random, true).map(BiomeInfoMusicTests::location));
        assertEquals(Optional.of(id("two")), selector.select(two, Optional.empty(), random, false).map(BiomeInfoMusicTests::location));
    }

    @Test
    void anUpdateMakesANewChoice() {
        var info = info();
        var selector = new BiomeMusicSelector();
        IRandomizer random = Randomizer.create(42);
        info.update(musicRule(false, "one"));
        assertEquals(Optional.of(id("one")), selector.select(info, Optional.empty(), random, true).map(BiomeInfoMusicTests::location));
        info.update(musicRule(true, "two"));
        assertEquals(Optional.of(id("two")), selector.select(info, Optional.empty(), random, false).map(BiomeInfoMusicTests::location));
    }
}

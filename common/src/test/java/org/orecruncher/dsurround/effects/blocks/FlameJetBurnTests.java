package org.orecruncher.dsurround.effects.blocks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how hard a flame jet burns over its life.
 */
public class FlameJetBurnTests {

    @Test
    void flaresBurnsThenDiesDown() {
        int limit = 100;
        assertEquals(0F, FlameJetBurn.burn(0, limit), "nothing yet");
        assertEquals(0.5F, FlameJetBurn.burn(Math.round(limit * FlameJetBurn.FLARE / 2F), limit), 0.05F, "flaring up");
        assertEquals(1F, FlameJetBurn.burn(50, limit), "burning steadily");
        assertTrue(FlameJetBurn.burn(90, limit) < 1F, "dying down");
        assertEquals(0F, FlameJetBurn.burn(limit, limit), "out");
    }

    @Test
    void neverOutsideZeroToOne() {
        for (int age = -5; age <= 110; age++) {
            float burn = FlameJetBurn.burn(age, 100);
            assertTrue(burn >= 0F && burn <= 1F, "at " + age);
        }
        assertEquals(0F, FlameJetBurn.burn(5, 0), "no life to burn in");
    }

    @Test
    void lavaThrowsDropletsSomeOfTheTime() {
        assertEquals(0, FlameJetBurn.droplets(3, 0F, 1F, 0F, 0.5F), "not burning");
        assertEquals(0, FlameJetBurn.droplets(3, 1F, 1F, FlameJetBurn.DROPLET_CHANCE, 0.5F), "not this time");
        assertTrue(FlameJetBurn.droplets(3, 1F, 1F, 0F, 0.5F) > 0, "this time");
        // Lowered particles: the same roll throws none
        assertEquals(0, FlameJetBurn.droplets(3, 1F, 0.2F, FlameJetBurn.DROPLET_CHANCE * 0.5F, 0.5F));
    }

    @Test
    void deeperLavaThrowsMoreDroplets() {
        assertEquals(1, FlameJetBurn.droplets(1, 1F, 1F, 0F, 0.99F), "shallow: one");
        assertTrue(FlameJetBurn.droplets(6, 1F, 1F, 0F, 0.99F) > 1, "deep: more");
        for (int strength = 1; strength <= 20; strength++)
            for (float count = 0F; count < 1F; count += 0.1F)
                assertTrue(FlameJetBurn.droplets(strength, 1F, 1F, 0F, count) <= FlameJetBurn.MAX_DROPLETS);
    }

    @Test
    void dropletsFlyHigherFromDeeperLavaBurningHarder() {
        assertTrue(FlameJetBurn.dropletSpeed(6, 1F, 0.5F) > FlameJetBurn.dropletSpeed(1, 1F, 0.5F));
        assertTrue(FlameJetBurn.dropletSpeed(3, 1F, 0.5F) > FlameJetBurn.dropletSpeed(3, 0.2F, 0.5F));
        // Not into the sky however deep
        assertEquals(FlameJetBurn.dropletSpeed(8, 1F, 0.5F), FlameJetBurn.dropletSpeed(30, 1F, 0.5F), 1e-9);
    }
}

package org.orecruncher.dsurround.effects.precipitation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the drips after rain: how hard it drips as things dry, how many columns are looked at, and what counts as
 * an edge. Hanging the drips needs a level, so is seen in the game.
 */
public class AfterRainDripsTests {

    @Test
    void dripsAsHardAsItRainedThenLessAndLess() {
        assertEquals(0.6F, AfterRainDrips.dripStrength(0.6F, 0), 1e-6F, "while it rains");
        var previous = 0.6F;
        for (int ticks = 1; ticks < AfterRainDrips.DRY_TICKS; ticks += 50) {
            var strength = AfterRainDrips.dripStrength(0.6F, ticks);
            assertTrue(strength < previous, "should keep easing off");
            assertTrue(strength > 0F, "dry too soon");
            previous = strength;
        }
        assertEquals(0F, AfterRainDrips.dripStrength(0.6F, AfterRainDrips.DRY_TICKS));
        // Quickly at first: a quarter of the way to dry, it drips at about half
        assertEquals(0.6F * 0.5625F, AfterRainDrips.dripStrength(0.6F, AfterRainDrips.DRY_TICKS / 4), 1e-3F);
    }

    @Test
    void wetAsTheHardestRainThenDries() {
        var drips = new AfterRainDrips();
        assertEquals(0F, drips.strength(), "dry to start with");

        drips.soak(0.3F);
        drips.soak(0.8F);
        drips.soak(0.5F);
        assertEquals(0.8F, drips.strength(), 1e-6F, "as wet as the hardest rain");

        drips.soak(0F);
        assertTrue(drips.strength() < 0.8F && drips.strength() > 0F, "drying");
        for (int t = 1; t < AfterRainDrips.DRY_TICKS; t++)
            drips.soak(0F);
        assertEquals(0F, drips.strength(), "dry again");

        // A new storm starts from dry, not from the last one's wetness
        drips.soak(0.2F);
        assertEquals(0.2F, drips.strength(), 1e-6F);
    }

    @Test
    void rainAgainBeforeDryingStartsTheDryingOver() {
        var drips = new AfterRainDrips();
        drips.soak(0.5F);
        for (int t = 0; t < AfterRainDrips.DRY_TICKS / 2; t++)
            drips.soak(0F);
        drips.soak(0.1F);
        assertEquals(0.5F, drips.strength(), 1e-6F, "still as wet as the earlier rain");
    }

    @Test
    void clearingDriesAtOnce() {
        var drips = new AfterRainDrips();
        drips.soak(1F);
        drips.clear();
        assertEquals(0F, drips.strength());
    }

    @Test
    void triesAreTheWantedNumberOnAverage() {
        assertEquals(0, AfterRainDrips.tries(0F, 0F));
        assertEquals(2, AfterRainDrips.tries(2F, 0.5F));
        assertEquals(3, AfterRainDrips.tries(2.25F, 0.2F), "one more as often as the fraction");
        assertEquals(2, AfterRainDrips.tries(2.25F, 0.3F));

        int total = 0;
        for (int i = 0; i < 1000; i++)
            total += AfterRainDrips.tries(0.4F, i / 1000F);
        assertEquals(400, total);
    }

    @Test
    void anEdgeHasLowerGroundBesideIt() {
        // A roof's top block at 70, so its column's ground is 71
        assertTrue(AfterRainDrips.isEdge(70, 71, 71, 71, 64), "open to the ground on one side");
        assertTrue(AfterRainDrips.isEdge(70, 70, 71, 71, 71), "a step down beside it");
        assertFalse(AfterRainDrips.isEdge(70, 71, 71, 71, 71), "roof all around: the middle of a roof");
        assertFalse(AfterRainDrips.isEdge(70, 72, 75, 71, 71), "higher all around");
    }
}

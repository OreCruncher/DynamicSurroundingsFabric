// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.rainbow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for when there is a rainbow: as rain eases off, for a while after it stops, not as it picks up, and the same for
 * every player.
 */
public class RainbowConditionsTests {

    private static final float ALWAYS = 1F;
    private static final float NEVER = 0F;

    // Ticks the conditions from a game time through a rain level easing from one value to another
    private static long rain(RainbowConditions conditions, long time, float from, float to, int ticks, float chance) {
        for (int i = 0; i < ticks; i++) {
            float level = from + (to - from) * i / (float) Math.max(ticks - 1, 1);
            conditions.update(time++, level, chance);
        }
        return time;
    }

    private static long storm(RainbowConditions conditions, long time, float peak, float chance) {
        time = rain(conditions, time, 0F, peak, 200, chance);
        return rain(conditions, time, peak, peak, 600, chance);
    }

    @Test
    void noneWhileItRainsHard() {
        var conditions = new RainbowConditions();
        long time = storm(conditions, 1000L, 0.8F, ALWAYS);
        assertFalse(conditions.isClearing());
        assertEquals(0F, conditions.strength(time));
    }

    @Test
    void oneAsTheRainEasesToADrizzle() {
        var conditions = new RainbowConditions();
        long time = storm(conditions, 1000L, 0.5F, ALWAYS);
        time = rain(conditions, time, 0.5F, 0.05F, 300, ALWAYS);
        assertTrue(conditions.isClearing());
        assertTrue(conditions.hasRainbow());
        assertEquals(1F, conditions.strength(time), 0.01F);
    }

    @Test
    void noneAsTheRainPicksUp() {
        var conditions = new RainbowConditions();
        long time = rain(conditions, 1000L, 0F, 0.25F, 300, ALWAYS);
        assertFalse(conditions.isClearing(), "rising rain isn't clearing");
        assertEquals(0F, conditions.strength(time));
    }

    @Test
    void endsWhenTheRainPicksUpAgain() {
        var conditions = new RainbowConditions();
        long time = storm(conditions, 1000L, 0.5F, ALWAYS);
        time = rain(conditions, time, 0.5F, 0.1F, 200, ALWAYS);
        assertTrue(conditions.isClearing());
        rain(conditions, time, 0.1F, 0.6F, 200, ALWAYS);
        assertFalse(conditions.isClearing());
    }

    @Test
    void lastsAWhileAfterTheRainStopsThenFades() {
        var conditions = new RainbowConditions();
        long time = storm(conditions, 1000L, 0.5F, ALWAYS);
        time = rain(conditions, time, 0.5F, 0F, 100, ALWAYS);
        long stopped = time;
        time = rain(conditions, time, 0F, 0F, RainbowConditions.CLEARING_TICKS / 2, ALWAYS);
        assertEquals(1F, conditions.strength(time), 0.01F, "still full halfway");
        time = rain(conditions, time, 0F, 0F, RainbowConditions.CLEARING_TICKS / 3, ALWAYS);
        float fading = conditions.strength(time);
        assertTrue(fading > 0F && fading < 1F, "fading: " + fading);
        rain(conditions, time, 0F, 0F, RainbowConditions.CLEARING_TICKS, ALWAYS);
        assertFalse(conditions.isClearing(), "over " + (time - stopped) + " ticks after");
    }

    @Test
    void aDrizzleThatEndsLeavesOne() {
        // Light rain never falls below FADING from above it: its ending is the clearing
        var conditions = new RainbowConditions();
        long time = storm(conditions, 1000L, 0.2F, ALWAYS);
        assertFalse(conditions.isClearing());
        time = rain(conditions, time, 0.2F, 0F, 40, ALWAYS);
        assertTrue(conditions.isClearing());
        assertTrue(conditions.strength(time) > 0.9F);
    }

    @Test
    void tooLittleRainLeavesNone() {
        var conditions = new RainbowConditions();
        long time = storm(conditions, 1000L, 0.05F, ALWAYS);
        rain(conditions, time, 0.05F, 0F, 40, ALWAYS);
        assertFalse(conditions.isClearing());
    }

    @Test
    void onlyAStrongStormLeavesASecondBow() {
        var weak = new RainbowConditions();
        long time = storm(weak, 1000L, 0.5F, ALWAYS);
        rain(weak, time, 0.5F, 0F, 100, ALWAYS);
        assertTrue(weak.hasRainbow());
        assertFalse(weak.secondary());

        var strong = new RainbowConditions();
        time = storm(strong, 1000L, 0.9F, ALWAYS);
        rain(strong, time, 0.9F, 0F, 100, ALWAYS);
        assertTrue(strong.hasRainbow());
        assertTrue(strong.secondary());
    }

    @Test
    void theChanceDecides() {
        var conditions = new RainbowConditions();
        long time = storm(conditions, 1000L, 0.5F, NEVER);
        time = rain(conditions, time, 0.5F, 0F, 100, NEVER);
        assertTrue(conditions.isClearing());
        assertFalse(conditions.hasRainbow());
        assertEquals(0F, conditions.strength(time));
    }

    @Test
    void aboutAsOftenAsTheChanceAndTheSameForEveryone() {
        int with = 0;
        int clearings = 400;
        for (int i = 0; i < clearings; i++) {
            long start = 100_000L + i * 50_000L;
            var one = new RainbowConditions();
            var other = new RainbowConditions();
            for (var conditions : new RainbowConditions[]{one, other}) {
                long time = storm(conditions, start, 0.5F, 0.5F);
                rain(conditions, time, 0.5F, 0F, 100, 0.5F);
            }
            assertEquals(one.hasRainbow(), other.hasRainbow());
            assertEquals(one.seed(), other.seed());
            if (one.hasRainbow())
                with++;
        }
        assertEquals(0.5, with / (double) clearings, 0.08);
    }

    @Test
    void theSunMustBeUpAndLow() {
        for (var secondary : new boolean[]{false, true}) {
            assertEquals(0F, RainbowConditions.sunFactor(-5F, secondary));
            assertEquals(0F, RainbowConditions.sunFactor(0F, secondary));
            assertEquals(0.5F, RainbowConditions.sunFactor(1.5F, secondary), 1.0E-5F);
            assertEquals(1F, RainbowConditions.sunFactor(20F, secondary));
            assertEquals(1F, RainbowConditions.sunFactor(42F, secondary));
            assertEquals(0F, RainbowConditions.sunFactor(55F, secondary));
        }
    }

    @Test
    void aSecondaryBowShowsWithTheSunHigher() {
        // Its top, 51 degrees from the point opposite the sun, is above the horizon once the primary's is below it
        assertEquals(0F, RainbowConditions.sunFactor(47F, false));
        assertEquals(1F, RainbowConditions.sunFactor(47F, true));
        assertEquals(0F, RainbowConditions.sunFactor(51F, true));
    }

    @Test
    void theSunsElevationFromItsAngle() {
        assertEquals(90F, RainbowConditions.sunElevation(0F), 1.0E-3F);
        assertEquals(0F, RainbowConditions.sunElevation(90F), 1.0E-3F);
        assertEquals(-90F, RainbowConditions.sunElevation(180F), 1.0E-3F);
        assertEquals(20F, RainbowConditions.sunElevation(70F), 1.0E-3F);
        assertEquals(20F, RainbowConditions.sunElevation(290F), 1.0E-3F);
    }

    @Test
    void resetForgetsTheStorm() {
        var conditions = new RainbowConditions();
        long time = storm(conditions, 1000L, 0.9F, ALWAYS);
        rain(conditions, time, 0.9F, 0F, 100, ALWAYS);
        conditions.reset();
        assertFalse(conditions.isClearing());
        assertEquals(0F, conditions.stormPeak());
    }

    @Test
    void aMoonbowNeedsTheMoonNearFull() {
        assertEquals(1F, RainbowConditions.moonFactor(1F));
        assertEquals(0.5F, RainbowConditions.moonFactor(0.75F));
        assertEquals(0F, RainbowConditions.moonFactor(0.5F));
        assertEquals(0F, RainbowConditions.moonFactor(0F));
    }
}

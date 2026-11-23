// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for whether a rainbow or a moonbow shows, and how one hands over to the other.
 */
public class RainbowHandlerTests {

    private static final float LOW_SUN = 0.1F;
    private static final float SUN = 0.8F;
    private static final float MOON = 0.3F;

    // A rain biome, the rain clearing, and a bow this time
    private static RainbowHandler.Decision clearing(float elevation, float sun, float moon, boolean moonbow, float strength) {
        return RainbowHandler.decide(true, true, true, elevation, sun, moon, moonbow, strength);
    }

    @Test
    void aLowSunAfterRainShowsARainbow() {
        var decision = clearing(LOW_SUN, SUN, 0F, false, 0F);
        assertEquals("showing", decision.status());
        assertFalse(decision.moonbow());
        assertEquals(SUN, decision.light());
    }

    @Test
    void aLowFullMoonShowsAMoonbow() {
        var decision = clearing(-LOW_SUN, 0F, MOON, false, 0F);
        assertEquals("showing a moonbow", decision.status());
        assertTrue(decision.moonbow());
        assertEquals(MOON, decision.light());
    }

    @Test
    void eachMissingConditionShowsNone() {
        assertEquals("not a rain biome", RainbowHandler.decide(false, true, true, LOW_SUN, SUN, 0F, false, 0F).status());
        assertEquals("rain not clearing", RainbowHandler.decide(true, false, true, LOW_SUN, SUN, 0F, false, 0F).status());
        assertEquals("none this time", RainbowHandler.decide(true, true, false, LOW_SUN, SUN, 0F, false, 0F).status());
        assertEquals("sun too high", clearing(0.9F, 0F, 0F, false, 0F).status());
        assertEquals("sun down, moon not full or not low", clearing(-0.5F, 0F, 0F, false, 0F).status());
        for (var decision : new RainbowHandler.Decision[]{
                RainbowHandler.decide(false, true, true, LOW_SUN, SUN, 0F, false, 0.5F),
                RainbowHandler.decide(true, false, true, LOW_SUN, SUN, 0F, false, 0.5F),
                RainbowHandler.decide(true, true, false, LOW_SUN, SUN, 0F, false, 0.5F),
                clearing(0.9F, 0F, 0F, false, 0.5F)})
            assertEquals(0F, decision.light(), decision.status() + ": fades away");
    }

    @Test
    void aFadingMoonbowStaysAMoonbowAtDawn() {
        // The sun has come up while a moonbow still shows: it fades away as a moonbow, never in a rainbow's colors
        var decision = clearing(LOW_SUN, SUN, 0F, true, 0.4F);
        assertEquals("changing to a rainbow", decision.status());
        assertTrue(decision.moonbow());
        assertEquals(0F, decision.light());
        // Once it has faded, the rainbow shows
        var after = clearing(LOW_SUN, SUN, 0F, true, 0F);
        assertFalse(after.moonbow());
        assertEquals(SUN, after.light());
    }

    @Test
    void aFadingRainbowStaysARainbowAtDusk() {
        var decision = clearing(-LOW_SUN, 0F, MOON, false, 0.4F);
        assertEquals("changing to a moonbow", decision.status());
        assertFalse(decision.moonbow());
        assertEquals(0F, decision.light());
        assertTrue(clearing(-LOW_SUN, 0F, MOON, false, 0F).moonbow());
    }

    @Test
    void aBowFadingForWantOfRainKeepsWhatItIs() {
        // The rain stops clearing while a moonbow fades: still a moonbow, so it doesn't change color as it goes
        assertTrue(RainbowHandler.decide(true, false, true, -LOW_SUN, 0F, MOON, true, 0.4F).moonbow());
        assertFalse(RainbowHandler.decide(true, false, true, LOW_SUN, SUN, 0F, false, 0.4F).moonbow());
    }
}

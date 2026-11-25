// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.groundfog;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.testing.VanillaSun;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how strong the ground fog is for the time of day, the wind, rain, and being out in it. Times of day are
 * given as vanilla's ticks and turned into the sun's angle as vanilla's day timeline does, so the mist is shown to
 * keep vanilla's times.
 */
public class GroundFogConditionsTests {

    /**
     * The sun's angle at a time of vanilla's day, in ticks, as its timeline gives it
     */
    static float sun(long ticks) {
        return VanillaSun.angle(ticks);
    }

    @Test
    void theThresholdsAreVanillasTimes() {
        // Where vanilla's sun is when the mist used to start and finish changing, by the clock
        assertEquals(sun(11000), GroundFogConditions.GATHERS_FROM, 0.01F);
        assertEquals(sun(13000), GroundFogConditions.GATHERED_AT, 0.01F);
        assertEquals(sun(1000), GroundFogConditions.BURNS_OFF_FROM, 0.01F);
        assertEquals(sun(3000), GroundFogConditions.BURNED_OFF_AT, 0.01F);
    }

    @Test
    void thickestAtNightAndDawn() {
        assertEquals(1F, GroundFogConditions.timeOfDay(sun(18000)), 1e-6F, "midnight");
        assertEquals(1F, GroundFogConditions.timeOfDay(sun(500)), 1e-6F, "just after sunrise");
        assertEquals(1F, GroundFogConditions.timeOfDay(sun(23500)), 1e-6F, "just before sunrise");
    }

    @Test
    void burnsOffByMidMorningToAThinHaze() {
        assertEquals(GroundFogConditions.DAYTIME, GroundFogConditions.timeOfDay(sun(6000)), 1e-6F, "noon");
        float burning = GroundFogConditions.timeOfDay(sun(2000));
        assertTrue(burning < 1F && burning > GroundFogConditions.DAYTIME, "burning off: " + burning);
        assertTrue(GroundFogConditions.DAYTIME > 0F, "it lingers through the day");
    }

    @Test
    void gathersAgainInTheEvening() {
        float gathering = GroundFogConditions.timeOfDay(sun(12000));
        assertTrue(gathering > GroundFogConditions.DAYTIME && gathering < 1F, "gathering: " + gathering);
        assertEquals(1F, GroundFogConditions.timeOfDay(sun(13000)), 1e-6F, "by nightfall");
    }

    @Test
    void changesSmoothlyThroughTheDay() {
        float last = GroundFogConditions.timeOfDay(sun(0));
        for (long t = 50; t < 24000; t += 50) {
            float now = GroundFogConditions.timeOfDay(sun(t));
            assertTrue(Math.abs(now - last) < 0.05F, "a jump at " + t);
            last = now;
        }
        // Angles past a full turn, or below 0, are the same time of day
        assertEquals(GroundFogConditions.timeOfDay(10F), GroundFogConditions.timeOfDay(370F), 1e-6F);
        assertEquals(GroundFogConditions.timeOfDay(300F), GroundFogConditions.timeOfDay(-60F), 1e-6F);
    }

    @Test
    void followsTheSunWhereTheDayIsShorter() {
        // A winter day with a late sunrise (Ecliptic Seasons): at the clock's usual mid-morning the sun is still low,
        // so the mist hasn't burned off
        float lowSun = GroundFogConditions.BURNS_OFF_FROM - 5F;
        assertEquals(1F, GroundFogConditions.timeOfDay(lowSun), 1e-6F);
    }

    @Test
    void aStrongWindThinsIt() {
        assertEquals(1F, GroundFogConditions.wind(20F), 1e-6F, "a breeze");
        assertEquals(1F, GroundFogConditions.wind(GroundFogConditions.WIND_THINS_FROM), 1e-6F);
        assertTrue(GroundFogConditions.wind(130F) < 1F, "a thunderstorm's");
        assertEquals(GroundFogConditions.WINDIEST, GroundFogConditions.wind(250F), 1e-6F, "a dust storm's");
    }

    @Test
    void rainBreaksItUp() {
        assertEquals(1F, GroundFogConditions.rain(0F), 1e-6F);
        assertEquals(1F - GroundFogConditions.RAIN_BREAKS_UP, GroundFogConditions.rain(1F), 1e-6F);
        assertTrue(GroundFogConditions.rain(0.5F) < 1F);
    }

    @Test
    void underCoverThereIsNone() {
        assertEquals(0F, GroundFogConditions.strength(sun(18000), 0F, 0F, 0F), 1e-6F);
        assertEquals(1F, GroundFogConditions.strength(sun(18000), 0F, 0F, 1F), 1e-6F, "out in it at night, calm and dry");
    }

    @Test
    void theConditionsCombine() {
        float all = GroundFogConditions.strength(sun(6000), 150F, 0.5F, 0.5F);
        float expected = GroundFogConditions.timeOfDay(sun(6000)) * GroundFogConditions.wind(150F) * GroundFogConditions.rain(0.5F) * 0.5F;
        assertEquals(expected, all, 1e-6F);
    }
}

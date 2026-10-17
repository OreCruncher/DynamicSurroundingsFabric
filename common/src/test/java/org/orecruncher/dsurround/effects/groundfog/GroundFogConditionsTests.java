package org.orecruncher.dsurround.effects.groundfog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how strong the ground fog is for the time of day, the wind, rain, and being out in it.
 */
public class GroundFogConditionsTests {

    @Test
    void thickestAtNightAndDawn() {
        assertEquals(1F, GroundFogConditions.timeOfDay(18000), 1e-6F, "midnight");
        assertEquals(1F, GroundFogConditions.timeOfDay(500), 1e-6F, "just after sunrise");
        assertEquals(1F, GroundFogConditions.timeOfDay(23500), 1e-6F, "just before sunrise");
    }

    @Test
    void burnsOffByMidMorningToAThinHaze() {
        assertEquals(GroundFogConditions.DAYTIME, GroundFogConditions.timeOfDay(6000), 1e-6F, "noon");
        float burning = GroundFogConditions.timeOfDay(2000);
        assertTrue(burning < 1F && burning > GroundFogConditions.DAYTIME, "burning off: " + burning);
        assertTrue(GroundFogConditions.DAYTIME > 0F, "it lingers through the day");
    }

    @Test
    void gathersAgainInTheEvening() {
        float gathering = GroundFogConditions.timeOfDay(12000);
        assertTrue(gathering > GroundFogConditions.DAYTIME && gathering < 1F, "gathering: " + gathering);
        assertEquals(1F, GroundFogConditions.timeOfDay(13000), 1e-6F, "by nightfall");
    }

    @Test
    void changesSmoothlyThroughTheDay() {
        float last = GroundFogConditions.timeOfDay(0);
        for (long t = 50; t < 24000; t += 50) {
            float now = GroundFogConditions.timeOfDay(t);
            assertTrue(Math.abs(now - last) < 0.05F, "a jump at " + t);
            last = now;
        }
        // Days after the first are the same
        assertEquals(GroundFogConditions.timeOfDay(6000), GroundFogConditions.timeOfDay(30000), 1e-6F);
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
        assertEquals(0F, GroundFogConditions.strength(18000, 0F, 0F, 0F), 1e-6F);
        assertEquals(1F, GroundFogConditions.strength(18000, 0F, 0F, 1F), 1e-6F, "out in it at night, calm and dry");
    }

    @Test
    void theConditionsCombine() {
        float all = GroundFogConditions.strength(6000, 150F, 0.5F, 0.5F);
        float expected = GroundFogConditions.timeOfDay(6000) * GroundFogConditions.wind(150F) * GroundFogConditions.rain(0.5F) * 0.5F;
        assertEquals(expected, all, 1e-6F);
    }
}

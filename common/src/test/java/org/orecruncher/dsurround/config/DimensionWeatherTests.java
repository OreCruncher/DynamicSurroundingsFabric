package org.orecruncher.dsurround.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the guess at whether a dimension draws its own weather and clouds, which decides whether particle weather
 * and the mod's clouds are used there when no dimension rule says. On 26.2 NeoForge says, through the dimension's
 * weather and clouds renderers (set by the NeoForge platform); nothing replaces a dimension's on Fabric.
 */
public class DimensionWeatherTests {

    @Test
    void withoutAPlatformSayingOtherwiseEveryDimensionGetsParticleWeather() {
        assertFalse(DimensionInfo.drawsOwnWeather.test(null));
    }

    @Test
    void withoutAPlatformSayingOtherwiseEveryDimensionGetsTheModsClouds() {
        assertFalse(DimensionInfo.drawsOwnClouds.test(null));
    }
}

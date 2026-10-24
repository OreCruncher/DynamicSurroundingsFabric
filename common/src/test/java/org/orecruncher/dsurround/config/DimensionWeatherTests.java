package org.orecruncher.dsurround.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the guess at whether a dimension draws its own weather, which decides whether particle weather is used
 * there when no dimension rule says. On 26.2 NeoForge says, through the dimension's weather renderer (set by the
 * NeoForge platform); nothing replaces a dimension's weather on Fabric.
 */
public class DimensionWeatherTests {

    @Test
    void withoutAPlatformSayingOtherwiseEveryDimensionGetsParticleWeather() {
        assertFalse(DimensionInfo.drawsOwnWeather.test(null));
    }
}

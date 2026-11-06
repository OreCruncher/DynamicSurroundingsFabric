// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the guess at whether a dimension draws its own weather and clouds, which decides whether particle weather
 * and the mod's clouds are used there when no dimension rule says. On 26.2 NeoForge says, through the dimension's
 * weather and clouds renderers (set by the NeoForge platform); nothing replaces a dimension's on Fabric.
 */
public class DimensionWeatherTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void withoutAPlatformSayingOtherwiseEveryDimensionGetsParticleWeather() {
        assertFalse(DimensionInfo.drawsOwnWeather.test(null));
    }

    @Test
    void withoutAPlatformSayingOtherwiseEveryDimensionGetsTheModsClouds() {
        assertFalse(DimensionInfo.drawsOwnClouds.test(null));
    }
}

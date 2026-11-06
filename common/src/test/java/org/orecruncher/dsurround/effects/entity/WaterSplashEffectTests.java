// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WaterSplashEffectTests {

    @Test
    void aPlayerFallingInMakesRings() {
        assertTrue(WaterSplashEffect.makesRings(0.6F, 0.5D));
    }

    @Test
    void aSmallBodyMakesNone() {
        assertFalse(WaterSplashEffect.makesRings(0.4F, 2D));
    }

    @Test
    void wadingInMakesNone() {
        assertFalse(WaterSplashEffect.makesRings(0.6F, 0.05D));
        assertFalse(WaterSplashEffect.makesRings(0.6F, -0.2D));
    }

    @Test
    void biggerAndFasterMakesBiggerRingsUpToALimit() {
        assertTrue(WaterSplashEffect.ringSize(0.6F, 1D) > WaterSplashEffect.ringSize(0.6F, 0.2D));
        assertTrue(WaterSplashEffect.ringSize(1.4F, 1D) > WaterSplashEffect.ringSize(0.6F, 1D));
        assertEquals(WaterSplashEffect.ringSize(4F, 3D), WaterSplashEffect.ringSize(4F, 30D));
    }
}

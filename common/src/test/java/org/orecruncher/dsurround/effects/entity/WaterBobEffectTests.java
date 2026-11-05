// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WaterBobEffectTests {

    @Test
    void theBottomOfABobIsWhereSinkingTurnsToRising() {
        assertTrue(WaterBobEffect.turnedUp(-0.05D, 0.02D));
        assertTrue(WaterBobEffect.turnedUp(-0.05D, 0D));
    }

    @Test
    void risingOrFallingOrStayingPutIsNoBob() {
        assertFalse(WaterBobEffect.turnedUp(0.02D, 0.03D));
        assertFalse(WaterBobEffect.turnedUp(-0.05D, -0.02D));
        assertFalse(WaterBobEffect.turnedUp(0D, 0D));
    }

    @Test
    void aWakeSpreadsFasterTheFasterItsBodyMovesUpToALimit() {
        assertTrue(WaterBobEffect.wakeSize(0.6F, 0.2D) > WaterBobEffect.wakeSize(0.6F, 0.05D));
        assertEquals(WaterBobEffect.wakeSize(0.6F, 5D), WaterBobEffect.wakeSize(0.6F, 50D));
    }

    @Test
    void floatingMeansTheSurfaceCrossesTheBody() {
        assertTrue(WaterBobEffect.floats(62D, 1.8F, 63D));
        assertFalse(WaterBobEffect.floats(58D, 1.8F, 63D));
        assertFalse(WaterBobEffect.floats(63.5D, 1.8F, 63D));
    }
}

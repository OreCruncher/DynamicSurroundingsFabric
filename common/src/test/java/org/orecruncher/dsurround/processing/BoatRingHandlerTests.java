// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BoatRingHandlerTests {

    @Test
    void boatsTakeTurnsRatherThanKeepingTime() {
        assertTrue(BoatRingHandler.ringDue(40, 0, 40));
        assertFalse(BoatRingHandler.ringDue(40, 1, 40));
        assertTrue(BoatRingHandler.ringDue(39, 1, 40));
    }

    @Test
    void aFasterBoatsRingsComeSoonerAndAtLeastOneTickApart() {
        assertTrue(BoatRingHandler.wakeTicks(0.4D) < BoatRingHandler.wakeTicks(0.1D));
        assertEquals(1, BoatRingHandler.wakeTicks(10D));
    }

    @Test
    void aFasterBoatsRingsSpreadFasterUpToALimit() {
        assertTrue(BoatRingHandler.wakeSize(0.3D) > BoatRingHandler.wakeSize(0.1D));
        assertEquals(BoatRingHandler.wakeSize(5D), BoatRingHandler.wakeSize(50D));
    }
}

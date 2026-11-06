// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WorldChangeTrackerTests {

    @Test
    void aChangeNearbyCounts() {
        assertTrue(WorldChangeTracker.isNear(10D, -3D, 20D));
        assertTrue(WorldChangeTracker.isNear(WorldChangeTracker.NEAR, 0D, 0D));
    }

    @Test
    void grassSpreadingFarOffDoesNot() {
        assertFalse(WorldChangeTracker.isNear(WorldChangeTracker.NEAR + 1D, 0D, 0D));
        assertFalse(WorldChangeTracker.isNear(170D, 0D, 0D));
    }

    @Test
    void aChangeHighAboveOrFarBelowIsFarToo() {
        assertFalse(WorldChangeTracker.isNear(0D, 100D, 0D));
    }

    @Test
    void aChunkCountsIfAnyOfItCouldBeNear() {
        assertTrue(WorldChangeTracker.isNearColumn(WorldChangeTracker.NEAR + 10D, 0D, 12D));
        assertFalse(WorldChangeTracker.isNearColumn(WorldChangeTracker.NEAR + 20D, 0D, 12D));
    }

    @Test
    void generationGoesUpWhenSomethingChanges() {
        long before = WorldChangeTracker.generation();
        WorldChangeTracker.changed();
        assertTrue(WorldChangeTracker.generation() > before);
    }
}

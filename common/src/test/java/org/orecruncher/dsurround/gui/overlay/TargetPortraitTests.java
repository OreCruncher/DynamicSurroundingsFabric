// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.gui.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TargetPortraitTests {

    @Test
    void theBarFillsWithHealth() {
        assertEquals(100, TargetPortraitOverlay.barFill(20F, 20F, 100));
        assertEquals(50, TargetPortraitOverlay.barFill(10F, 20F, 100));
    }

    @Test
    void aMobBarelyAliveStillShowsOnTheBar() {
        assertEquals(1, TargetPortraitOverlay.barFill(0.1F, 1024F, 100));
    }

    @Test
    void aDeadMobsBarIsEmpty() {
        assertEquals(0, TargetPortraitOverlay.barFill(0F, 20F, 100));
    }

    @Test
    void healthPastItsMaximumStaysOnTheBar() {
        assertEquals(100, TargetPortraitOverlay.barFill(30F, 20F, 100));
    }

    @Test
    void aBiggerMobShowsMoreBlocksUpToALimit() {
        assertTrue(TargetPortraitOverlay.headBlocks(3F, 0.6F) > TargetPortraitOverlay.headBlocks(1.9F, 0.6F));
        assertEquals(TargetPortraitOverlay.headBlocks(50F, 0.6F), TargetPortraitOverlay.headBlocks(80F, 0.6F));
    }

    @Test
    void aWideMobShowsMoreThanItsHeightAlone() {
        assertTrue(TargetPortraitOverlay.headBlocks(1.6F, 1.4F) > TargetPortraitOverlay.headBlocks(1.6F, 0.6F));
    }

    @Test
    void aSmallMobIsZoomedInOnToALimit() {
        assertEquals(0.45F, TargetPortraitOverlay.headBlocks(0.3F, 0.3F));
    }
}

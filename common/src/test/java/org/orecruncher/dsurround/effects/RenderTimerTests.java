// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for a render timer's count: how many of what it draws there were a frame, for recordings.
 */
public class RenderTimerTests {

    @Test
    void aTimerThatDoesNotCountSaysSo() {
        assertTrue(Double.isNaN(new RenderTimer("aurora").getCount()));
    }

    @Test
    void theCountIsAveragedAsTheTimesAre() {
        var timer = new RenderTimer("drops");
        timer.count(6000);
        assertEquals(6000D, timer.getCount(), "the first frame as it is");
        for (int i = 0; i < 500; i++)
            timer.count(3000);
        assertEquals(3000D, timer.getCount(), 1D, "settled on the new count");
    }

    @Test
    void timestampsAreScaledToNanoseconds() {
        assertEquals(12345L, RenderTimer.nanos(12345L, 1F), "OpenGL's are nanoseconds");
        assertEquals(123450L, RenderTimer.nanos(12345L, 10F), "AMD's Vulkan ticks are 10 ns");
    }

    @Test
    void noneIsACount() {
        var timer = new RenderTimer("spots");
        timer.count(0);
        assertEquals(0D, timer.getCount());
    }
}

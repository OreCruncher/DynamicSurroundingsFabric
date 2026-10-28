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
    void noneIsACount() {
        var timer = new RenderTimer("spots");
        timer.count(0);
        assertEquals(0D, timer.getCount());
    }
}

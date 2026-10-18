package org.orecruncher.dsurround.lib.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for holding a config entry's tooltip back until the mouse rests on it.
 */
public class TooltipDelayTests {

    // A frame at 60 FPS, in nanoseconds
    private static final long FRAME = 16_666_667L;
    // An arbitrary start: System.nanoTime() can be any value, negative included
    private static final long START = -5_000_000_000L;

    /**
     * Asks once a frame for {@code frames} frames, the mouse still at {@code x}, {@code y}
     *
     * @return the time after the last frame, and whether the last frame showed the tooltip
     */
    private static boolean rest(TooltipDelay delay, long[] now, int frames, double x, double y, boolean buttonDown) {
        boolean ready = false;
        for (int i = 0; i < frames; i++) {
            ready = delay.ready(now[0], x, y, buttonDown);
            now[0] += FRAME;
        }
        return ready;
    }

    private static int framesFor(long nanos) {
        return (int) (nanos / FRAME) + 1;
    }

    @Test
    void showsOnceTheMouseHasRestedForTheDelay() {
        var delay = new TooltipDelay();
        long[] now = {START};
        assertFalse(rest(delay, now, framesFor(TooltipDelay.DELAY_NANOS) - 2, 10, 20, false), "too soon");
        assertTrue(rest(delay, now, 3, 10, 20, false), "after the delay");
        assertTrue(rest(delay, now, 30, 10, 20, false), "and stays");
    }

    @Test
    void movingTheMouseStartsTheWaitOver() {
        var delay = new TooltipDelay();
        long[] now = {START};
        assertTrue(rest(delay, now, framesFor(TooltipDelay.DELAY_NANOS) + 1, 10, 20, false));
        assertFalse(delay.ready(now[0], 11, 20, false), "moved a pixel");
        now[0] += FRAME;
        assertFalse(rest(delay, now, framesFor(TooltipDelay.DELAY_NANOS) - 2, 11, 20, false), "still waiting");
        assertTrue(rest(delay, now, 3, 11, 20, false));
    }

    @Test
    void nothingShowsWhileTheButtonIsHeld() {
        // Dragging a slider: the mouse may rest while the button is down, and nothing shows until it is let go
        var delay = new TooltipDelay();
        long[] now = {START};
        assertFalse(rest(delay, now, framesFor(TooltipDelay.DELAY_NANOS) * 3, 10, 20, true));
        assertFalse(rest(delay, now, framesFor(TooltipDelay.DELAY_NANOS) - 2, 10, 20, false), "just let go");
        assertTrue(rest(delay, now, 3, 10, 20, false));
    }

    @Test
    void comingBackAfterAGapIsANewHover() {
        // The mouse left and returned to the same spot, or the list scrolled the entry under a still mouse: the
        // entry wasn't asked in between, and waits again
        var delay = new TooltipDelay();
        long[] now = {START};
        assertTrue(rest(delay, now, framesFor(TooltipDelay.DELAY_NANOS) + 1, 10, 20, false));
        now[0] += TooltipDelay.HOVER_GAP_NANOS + FRAME;
        assertFalse(delay.ready(now[0], 10, 20, false));
    }

    @Test
    void aSlowFrameIsNotAGap() {
        var delay = new TooltipDelay();
        long[] now = {START};
        assertTrue(rest(delay, now, framesFor(TooltipDelay.DELAY_NANOS) + 1, 10, 20, false));
        now[0] += TooltipDelay.HOVER_GAP_NANOS / 2;
        assertTrue(delay.ready(now[0], 10, 20, false));
    }
}

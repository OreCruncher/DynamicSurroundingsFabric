package org.orecruncher.dsurround.runtime.weather;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the wind up high that carries the clouds: the same for everyone at a game time, however each got there.
 */
public class UpperWindTests {

    private static double[] drift(UpperWind wind, double gameTime) {
        var out = new double[2];
        wind.drift(gameTime, out);
        return out;
    }

    @Test
    void theSameForEveryoneAtAGameTime() {
        // One player has been here all along, a tick at a time; another arrives late; a third arrives later still
        var along = new UpperWind();
        for (long t = 0; t <= 50_000; t += 7)
            drift(along, t);
        var late = drift(new UpperWind(), 50_000);
        var now = drift(along, 50_000);
        assertEquals(late[0], now[0], 1e-6);
        assertEquals(late[1], now[1], 1e-6);
    }

    @Test
    void movesSmoothlyThroughTheMinutes() {
        var wind = new UpperWind();
        var last = drift(wind, 0);
        double lastStep = Double.NaN;
        for (double t = 0.5; t < 10 * UpperWind.KNOT_TICKS; t += 0.5) {
            var now = drift(wind, t);
            double step = Math.hypot(now[0] - last[0], now[1] - last[1]) / 0.5;
            // Never faster than the windiest minute
            assertTrue(step <= UpperWind.WINDIEST * (1 + UpperWind.GUSTINESS) + 1e-4, "too fast at " + t);
            // No jumps in speed, at a minute's turn or anywhere
            if (!Double.isNaN(lastStep))
                assertEquals(lastStep, step, 0.001, "a jump at " + t);
            lastStep = step;
            last = now;
        }
    }

    @Test
    void goesBackToTheStartWhenTimeDoes() {
        var wind = new UpperWind();
        drift(wind, 100_000);
        var back = drift(wind, 3_000);
        var fresh = drift(new UpperWind(), 3_000);
        assertEquals(fresh[0], back[0], 1e-9);
        assertEquals(fresh[1], back[1], 1e-9);
    }

    @Test
    void followsTheDaysWindRoughly() {
        // Over a day, the clouds go about the way the day's wind blows
        long day = 12;
        var wind = new UpperWind();
        var start = drift(wind, day * 24000D);
        var end = drift(wind, (day + 1) * 24000D - 1);
        double angle = Math.atan2(end[1] - start[1], end[0] - start[0]);
        double off = Math.abs(Math.IEEEremainder(angle - UpperWind.heading(day), 2 * Math.PI));
        assertTrue(off <= UpperWind.WANDER + 0.05, "off by " + off);
    }

    @Test
    void anOldWorldIsQuickToCatchUpWith() {
        // About four years of a server running day and night
        long gameTime = 2_500_000_000L;
        long start = System.nanoTime();
        var out = drift(new UpperWind(), gameTime);
        double ms = (System.nanoTime() - start) / 1e6;
        assertTrue(Double.isFinite(out[0]) && Double.isFinite(out[1]));
        assertTrue(ms < 500, "took " + ms + " ms");
    }
}

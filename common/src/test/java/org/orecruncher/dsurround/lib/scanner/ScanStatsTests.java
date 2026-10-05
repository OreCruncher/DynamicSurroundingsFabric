package org.orecruncher.dsurround.lib.scanner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ScanStatsTests {

    @Test
    void costPerBlockIsUnknownUntilBlocksAreRead() {
        var stats = new ScanStats();
        assertTrue(Double.isNaN(stats.nanosPerBlock()));
        assertTrue(stats.summary().contains("n/a ns/block"), stats.summary());

        // Idle ticks, or only skipping air, don't produce a figure
        stats.record(100, 0, 0);
        stats.record(5_000, 0, 12);
        assertTrue(Double.isNaN(stats.nanosPerBlock()));
    }

    @Test
    void costPerBlockFromBusyTicks() {
        var stats = new ScanStats();

        for (int i = 0; i < 30; i++)
            stats.record(1_000_000, 20_000, 0);

        assertEquals(50, stats.nanosPerBlock(), 1.0E-6);
        assertTrue(stats.summary().contains("50 ns/block"), stats.summary());
    }

    @Test
    void costPerBlockDoesNotGrowWhileIdle() {
        // Regression: idle ticks cost a little time but read no blocks, so time/blocks grew without limit (to
        // millions of ns/block after ten seconds of standing still)
        var stats = new ScanStats();
        for (int i = 0; i < 10; i++)
            stats.record(1_000_000, 20_000, 0);

        for (int i = 0; i < 400; i++)
            stats.record(100, 0, 0);

        assertEquals(50, stats.nanosPerBlock(), 1.0E-6);
    }

    @Test
    void averageTimeStillIncludesIdleTicks() {
        // The per tick figures describe every tick: they should fall while idle
        var stats = new ScanStats();
        for (int i = 0; i < 10; i++)
            stats.record(1_000_000, 20_000, 0);
        double busyAverage = stats.averageMillis();

        for (int i = 0; i < 100; i++)
            stats.record(100, 0, 0);

        assertTrue(stats.averageMillis() < busyAverage / 100, "average " + stats.averageMillis());
    }

    @Test
    void costPerBlockFollowsNewWork() {
        var stats = new ScanStats();
        for (int i = 0; i < 50; i++)
            stats.record(1_000_000, 20_000, 0);
        for (int i = 0; i < 50; i++)
            stats.record(100, 0, 0);

        // Slower work after the idle spell is reflected
        for (int i = 0; i < 200; i++)
            stats.record(2_000_000, 20_000, 0);

        assertEquals(100, stats.nanosPerBlock(), 0.5);
    }
}

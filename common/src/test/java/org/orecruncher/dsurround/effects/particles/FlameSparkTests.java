package org.orecruncher.dsurround.effects.particles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how a spark cools and goes out.
 */
public class FlameSparkTests {

    @Test
    void brightFromTheStartThenGoesOut() {
        assertEquals(1F, FlameSpark.brightness(0F), "bright when thrown");
        assertEquals(1F, FlameSpark.brightness(0.5F), "still bright half way");
        assertTrue(FlameSpark.brightness(0.8F) < 1F && FlameSpark.brightness(0.8F) > 0F, "going out");
        assertEquals(0F, FlameSpark.brightness(1F), "out");
    }

    @Test
    void coolsFromWhiteYellowToDullRed() {
        var hot = FlameSpark.colorAt(0F).clone();
        var cool = FlameSpark.colorAt(1F).clone();
        assertTrue(hot[1] > cool[1] && hot[2] > cool[2], "less green and blue as it cools");
        assertTrue(cool[0] > cool[1] * 3F, "red at the end");
        assertTrue(cool[0] < hot[0], "dull rather than bright");
    }
}

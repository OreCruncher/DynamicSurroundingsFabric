package org.orecruncher.dsurround.effects.particles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how a tongue of flame's colour changes as it burns.
 */
public class FlameTongueTests {

    private static float[] at(float life) {
        return FlameTongue.colorAt(life).clone();
    }

    @Test
    void burnsWhiteYellowThenOrangeThenRed() {
        var hot = at(0F);
        var warm = at(0.4F);
        var cool = at(1F);
        assertTrue(hot[2] > warm[2] && warm[2] > cool[2], "less blue as it cools");
        assertTrue(hot[1] > warm[1] && warm[1] > cool[1], "less green as it cools");
        assertTrue(cool[0] > cool[1] * 3F, "red at the end");
        assertEquals(1F, hot[0], 1e-6F);
    }

    @Test
    void changesSmoothly() {
        var previous = at(0F);
        for (float life = 0.01F; life <= 1F; life += 0.01F) {
            var color = at(life);
            for (int i = 0; i < 3; i++)
                assertTrue(Math.abs(color[i] - previous[i]) < 0.05F, "jumped at " + life);
            previous = color;
        }
    }
}

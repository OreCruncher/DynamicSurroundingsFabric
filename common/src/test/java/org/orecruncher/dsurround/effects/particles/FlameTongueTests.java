package org.orecruncher.dsurround.effects.particles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how a tongue of flame's color changes as it burns.
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
    void soulFireBurnsWhiteCyanThenDeepBlue() {
        var hot = FlameTongue.colorAt(0F, FlameTongue.SOUL).clone();
        var cool = FlameTongue.colorAt(1F, FlameTongue.SOUL).clone();
        for (var color : new float[][]{hot, cool})
            assertTrue(color[2] > color[0], "bluer than red");
        assertTrue(hot[0] > cool[0] && hot[1] > cool[1], "darker as it cools");
        assertTrue(cool[2] > cool[1] * 2F, "deep blue at the end");
    }

    @Test
    void changesSmoothly() {
        for (var palette : new FlameTongue.Palette[]{FlameTongue.FIRE, FlameTongue.SOUL}) {
            var previous = FlameTongue.colorAt(0F, palette).clone();
            for (float life = 0.01F; life <= 1F; life += 0.01F) {
                var color = FlameTongue.colorAt(life, palette).clone();
                for (int i = 0; i < 3; i++)
                    assertTrue(Math.abs(color[i] - previous[i]) < 0.05F, "jumped at " + life);
                previous = color;
            }
        }
    }
}

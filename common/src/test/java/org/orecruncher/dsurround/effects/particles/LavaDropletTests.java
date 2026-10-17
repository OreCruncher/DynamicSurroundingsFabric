package org.orecruncher.dsurround.effects.particles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how a droplet of lava cools and goes out.
 */
public class LavaDropletTests {

    @Test
    void glowsUntilNearTheEndOfItsLife() {
        assertEquals(1F, LavaDroplet.brightness(0F), "glowing when thrown");
        assertEquals(1F, LavaDroplet.brightness(0.7F), "still glowing as it falls");
        assertTrue(LavaDroplet.brightness(0.9F) < 1F, "going out");
        assertEquals(0F, LavaDroplet.brightness(1F), "out");
    }

    @Test
    void coolsFromYellowOrangeToDullRed() {
        var hot = LavaDroplet.colorAt(0F).clone();
        var cool = LavaDroplet.colorAt(1F).clone();
        assertTrue(hot[1] > cool[1] && hot[2] > cool[2], "less green and blue as it cools");
        assertTrue(cool[0] > cool[1] * 3F, "red at the end");
        assertTrue(cool[0] < hot[0], "dull rather than bright");
    }
}

// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for which point lights are drawn when there are many: the nearest, the furthest of those fading out. Plain
 * Java: no game needed.
 */
public class PointLightFrameTests {

    private static final int STRIDE = PointLightFrame.STRIDE;

    // Lights at the given x, along a line, each with its index as its strength to tell them apart
    private static float[] lightsAt(float... xs) {
        var lights = new float[(xs.length + 1) * STRIDE];
        for (int l = 0; l < xs.length; l++) {
            int i = l * STRIDE;
            lights[i] = xs[l];
            lights[i + 1] = 64F;
            lights[i + 2] = -20F;
            lights[i + 3] = 0.2F;
            lights[i + 4] = 0.9F;
            lights[i + 5] = 0.4F;
            lights[i + 6] = l;
            lights[i + 7] = 1.75F;
        }
        return lights;
    }

    // n lights at x 0 to n - 1 from the camera, in a scrambled order, all at full strength
    private static float[] scrambled(int n) {
        var lights = new float[n * STRIDE];
        for (int l = 0; l < n; l++) {
            int i = l * STRIDE;
            lights[i] = (l * 7919) % n;
            lights[i + 3] = 1F;
            lights[i + 6] = 1F;
            lights[i + 7] = 2F;
        }
        return lights;
    }

    @Test
    void sortsNearestFirstKeepingEachLightWhole() {
        // The camera at x 10: distances 30, 2, 15, 0.5, 8
        var lights = lightsAt(40F, 8F, -5F, 10.5F, 18F);
        new PointLightFrame().sortNearest(lights, 5, 10.0, 64.0, -20.0);

        float[] expectedX = {10.5F, 8F, 18F, -5F, 40F};
        float[] expectedIndex = {3F, 1F, 4F, 2F, 0F};
        for (int r = 0; r < 5; r++) {
            int i = r * STRIDE;
            assertEquals(expectedX[r], lights[i], 1e-6F, "position of the light ranked " + r);
            assertEquals(expectedIndex[r], lights[i + 6], 1e-6F, "the light ranked " + r);
            assertEquals(0.9F, lights[i + 4], 1e-6F);
            assertEquals(1.75F, lights[i + 7], 1e-6F);
        }
        // Past the lights sorted, untouched
        assertEquals(0F, lights[5 * STRIDE + 7]);
    }

    @Test
    void sortsMoreLightsThanItHasRoomForYet() {
        int n = 300;
        var xs = new float[n];
        for (int l = 0; l < n; l++)
            xs[l] = (l * 7919) % n;
        var lights = lightsAt(xs);
        new PointLightFrame().sortNearest(lights, n, 0.0, 64.0, -20.0);
        for (int r = 0; r < n; r++)
            assertEquals(r, lights[r * STRIDE], 1e-6F);
    }

    @Test
    void theNearestAreDrawnInFullAndTheRestFadeOut() {
        assertEquals(1F, PointLightFrame.rankFade(0));
        assertEquals(1F, PointLightFrame.rankFade(PointLightFrame.FADE_FROM - 1));
        assertEquals(0F, PointLightFrame.rankFade(PointLightFrame.MAX_DRAWN));
        assertEquals(0F, PointLightFrame.rankFade(PointLightFrame.MAX_DRAWN + 100));
        float previous = 1F;
        for (int rank = PointLightFrame.FADE_FROM; rank <= PointLightFrame.MAX_DRAWN; rank++) {
            float fade = PointLightFrame.rankFade(rank);
            assertTrue(fade <= previous, "brighter further down at " + rank);
            assertTrue(previous - fade <= 1F / (PointLightFrame.MAX_DRAWN - PointLightFrame.FADE_FROM) + 1e-6F,
                    "jumps at " + rank);
            previous = fade;
        }
    }

    @Test
    void aFewLightsAreAllDrawnAsRecorded() {
        var lights = lightsAt(40F, 8F, -5F);
        // Strengths 0, 1 and 2: the first is too faint to draw
        var frame = new PointLightFrame();
        frame.select(lights, 3);

        assertEquals(2, frame.count());
        // Not sorted: too few to need it
        assertEquals(8F, frame.value(0, 0));
        assertEquals(1F, frame.value(0, 6));
        assertEquals(-5F, frame.value(1, 0));
        assertEquals(1.75F, frame.value(1, 7));
    }

    @Test
    void manyLightsAreTheNearestWithTheFurthestFaded() {
        int n = 200;
        var frame = new PointLightFrame();
        frame.select(scrambled(n), n);

        // At full strength even the last kept rank, at a sixteenth, is bright enough to draw
        assertEquals(PointLightFrame.MAX_DRAWN, frame.count());
        for (int l = 0; l < frame.count(); l++) {
            assertEquals(l, frame.value(l, 0), 1e-6F, "the light ranked " + l);
            assertEquals(PointLightFrame.rankFade(l), frame.value(l, 6), 1e-6F, "strength of the light ranked " + l);
        }
    }

    @Test
    void theFrameStartsOverEachTime() {
        var frame = new PointLightFrame();
        frame.select(scrambled(100), 100);
        frame.select(lightsAt(3F, 4F), 2);

        assertEquals(1, frame.count());
        assertEquals(4F, frame.value(0, 0));
    }
}

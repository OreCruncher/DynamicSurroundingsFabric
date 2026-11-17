// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
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

    // Where lights are placed on screen: looking down -z with no turn, a 70 degree, 16:9 projection
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f().perspective((float) Math.toRadians(70), 16F / 9F, 0.05F, 1000F);

    // One light at full strength at x, y, z from the camera, of the given radius, placed
    private static PointLightFrame placedAt(float x, float y, float z, float radius) {
        var lights = new float[STRIDE];
        lights[0] = x;
        lights[1] = y;
        lights[2] = z;
        lights[3] = lights[4] = lights[5] = 1F;
        lights[6] = 1F;
        lights[7] = radius;
        var frame = new PointLightFrame();
        frame.select(lights, 1);
        frame.place(VIEW, PROJECTION);
        return frame;
    }

    @Test
    void aLightAheadCoversItsSphereOnScreen() {
        var frame = placedAt(2F, 1F, -10F, 1.75F);

        assertEquals(1, frame.placedCount());
        assertEquals(-10F, frame.placed(0, 2), 1e-5F);
        assertEquals(1.75F, frame.placed(0, 3));
        float x0 = frame.placed(0, 8), y0 = frame.placed(0, 9), x1 = frame.placed(0, 10), y1 = frame.placed(0, 11);
        assertTrue(x0 < x1 && y0 < y1);
        assertTrue(x1 - x0 < 1F, "a small part of the screen");
        // Points all around the sphere's edge, as the camera sees it, fall within its part of the screen
        var center = new Vector3f(2F, 1F, -10F);
        var dir = new Vector3f(center).normalize();
        var a = new Vector3f(dir).cross(0F, 1F, 0F).normalize();
        var b = new Vector3f(a).cross(dir).normalize();
        for (int k = 0; k < 64; k++) {
            double angle = k * Math.PI * 2 / 64;
            var p = new Vector3f(center).add(new Vector3f(a).mul((float) Math.cos(angle) * 1.75F)).add(new Vector3f(b).mul((float) Math.sin(angle) * 1.75F));
            var clip = PROJECTION.transform(new Vector4f(p, 1F));
            float nx = clip.x / clip.w, ny = clip.y / clip.w;
            assertTrue(nx >= x0 - 1e-5F && nx <= x1 + 1e-5F && ny >= y0 - 1e-5F && ny <= y1 + 1e-5F, "edge point " + k);
        }
        // The area all the lights cover is this one's
        assertEquals(x0, frame.area(0));
        assertEquals(y1, frame.area(3));
    }

    @Test
    void aLightAroundTheCameraCoversTheWholeScreen() {
        var frame = placedAt(0.5F, 0F, 0.5F, 1.75F);

        assertEquals(1, frame.placedCount());
        assertEquals(-1F, frame.placed(0, 8));
        assertEquals(-1F, frame.placed(0, 9));
        assertEquals(1F, frame.placed(0, 10));
        assertEquals(1F, frame.placed(0, 11));
    }

    @Test
    void lightsBehindTheCameraOrOffScreenAreLeftOut() {
        assertEquals(0, placedAt(0F, 0F, 10F, 1.75F).placedCount(), "behind");
        assertEquals(0, placedAt(40F, 0F, -10F, 1.75F).placedCount(), "far to the side");
    }

    @Test
    void aLightPartlyOffScreenIsClippedToIt() {
        var frame = placedAt(13F, 0F, -10F, 1.75F);

        assertEquals(1, frame.placedCount());
        assertEquals(1F, frame.placed(0, 10));
        assertTrue(frame.placed(0, 8) < 1F);
    }
}

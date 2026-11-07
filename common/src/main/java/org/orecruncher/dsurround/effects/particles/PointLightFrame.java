// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import java.util.Arrays;

/**
 * Which point lights a frame draws, and how strongly: the nearest {@link #MAX_DRAWN} of those recorded, the furthest of
 * them fading out, and none too faint to change a pixel. No GPU in it: {@link PointLights} writes the quads from what
 * is chosen here. Kept and reused from frame to frame. Render thread only.
 */
public final class PointLightFrame {

    /**
     * The most lights drawn: the nearest, which are the brightest on screen and cost the most to draw (each is a
     * square over the pixels it may light, the whole screen when the camera is inside its reach). A swamp full of
     * fireflies could otherwise draw hundreds.
     */
    static final int MAX_DRAWN = 64;
    /**
     * From this far down the nearest, the lights fade, to nothing at {@link #MAX_DRAWN}: one moving across that limit
     * fades in or out rather than switching.
     */
    static final int FADE_FROM = 48;
    // A light this faint changes no pixel: under half a step of an 8 bit color, at its brightest
    static final float MIN_STRENGTH = 0.5F / 255F;
    /**
     * Each light's values: x, y, z (relative to the camera: small enough for a float wherever the world is), red,
     * green, blue, strength, radius
     */
    static final int STRIDE = 8;

    // For sorting the lights nearest first: each one's distance and index, and the lights in that order
    private long[] sortKeys = new long[64];
    private float[] sorted = new float[STRIDE * 64];
    // The lights chosen, laid out as they are recorded, their strength faded by rank
    private final float[] drawn = new float[STRIDE * MAX_DRAWN];
    private int count;

    /**
     * Chooses the lights to draw from the first {@code n} in {@code lights}, positioned relative to the camera. When
     * there are enough that some fade or are left out, it puts {@code lights} in order, nearest first.
     */
    public void select(float[] lights, int n) {
        if (n > FADE_FROM)
            this.sortNearest(lights, n, 0D, 0D, 0D);
        this.count = 0;
        for (int l = 0; l < Math.min(n, MAX_DRAWN); l++) {
            int i = l * STRIDE;
            float strength = lights[i + 6] * rankFade(l);
            if (strength < MIN_STRENGTH)
                continue;
            int o = this.count * STRIDE;
            System.arraycopy(lights, i, this.drawn, o, STRIDE);
            this.drawn[o + 6] = strength;
            this.count++;
        }
    }

    /**
     * How many lights were chosen
     */
    public int count() {
        return this.count;
    }

    /**
     * Value {@code k} (see {@link #STRIDE}) of chosen light {@code l}
     */
    public float value(int l, int k) {
        return this.drawn[l * STRIDE + k];
    }

    /**
     * How much the light {@code rank} lights down the nearest is drawn at: in full up to {@link #FADE_FROM}, fading
     * to nothing at {@link #MAX_DRAWN}.
     */
    static float rankFade(int rank) {
        if (rank < FADE_FROM)
            return 1F;
        return Math.max(0F, (float) (MAX_DRAWN - rank) / (MAX_DRAWN - FADE_FROM));
    }

    /**
     * Puts the first {@code n} lights in {@code lights} in order of how near each is to the camera, nearest first.
     */
    void sortNearest(float[] lights, int n, double cameraX, double cameraY, double cameraZ) {
        if (this.sortKeys.length < n)
            this.sortKeys = new long[lights.length / STRIDE];
        if (this.sorted.length < n * STRIDE)
            this.sorted = new float[lights.length];
        // Each light's distance squared, then its index: a float's bits order as it does when it isn't negative
        for (int l = 0; l < n; l++) {
            int i = l * STRIDE;
            double x = lights[i] - cameraX, y = lights[i + 1] - cameraY, z = lights[i + 2] - cameraZ;
            this.sortKeys[l] = ((long) Float.floatToIntBits((float) (x * x + y * y + z * z)) << 32) | l;
        }
        Arrays.sort(this.sortKeys, 0, n);
        for (int r = 0; r < n; r++)
            System.arraycopy(lights, (int) this.sortKeys[r] * STRIDE, this.sorted, r * STRIDE, STRIDE);
        System.arraycopy(this.sorted, 0, lights, 0, n * STRIDE);
    }
}

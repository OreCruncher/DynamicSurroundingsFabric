// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Arrays;

/**
 * Which point lights a frame draws, and how strongly: the nearest {@link #MAX_DRAWN} of those recorded, the furthest of
 * them fading out, and none too faint to change a pixel; and where each falls on screen ({@link #place}). No GPU in
 * it: {@link PointLights} packs what is worked out here into its uniform block. Kept and reused from frame to frame.
 * Render thread only.
 */
public final class PointLightFrame {

    /**
     * The most lights drawn: the nearest, which are the brightest on screen and cost the most to draw (each is looked at
     * by every pixel of the part of the screen the lights cover, and lit by the pixels in its own square, the whole
     * screen when the camera is inside its reach). A swamp full of fireflies could otherwise draw hundreds.
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
     * Each placed light's values: its center in view space x, y, z, its radius, red, green, blue, strength, and the part
     * of the screen it can light, x0, y0, x1, y1 (see {@link #place})
     */
    static final int PLACED_STRIDE = 12;
    // Within this many times its radius, a light is taken to cover the whole screen (as the square's corners would
    // reach behind the camera)
    static final float NEAR_FULL_SCREEN = 1.05F;
    private final float[] placed = new float[PLACED_STRIDE * MAX_DRAWN];
    private final float[] area = new float[4];
    private int placedCount;
    // Scratch for place
    private final Vector3f center = new Vector3f();
    private final Vector3f dir = new Vector3f();
    private final Vector3f up = new Vector3f();
    private final Vector3f right = new Vector3f();
    private final Vector4f corner = new Vector4f();
    private final float[] bounds = new float[4];

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
     * Where each chosen light falls on screen this frame, for drawing them all in one pass: its center in view space,
     * the part of the screen it can light, and the part all of them can. Lights wholly behind the camera or off screen
     * are left out. Read with {@link #placedCount}, {@link #placed} and {@link #area}.
     *
     * @param modelView  the camera's rotation, from the camera-relative positions the lights are recorded at
     * @param projection the frame's projection
     */
    public void place(Matrix4fc modelView, Matrix4fc projection) {
        this.placedCount = 0;
        this.area[0] = this.area[1] = Float.MAX_VALUE;
        this.area[2] = this.area[3] = -Float.MAX_VALUE;
        for (int l = 0; l < this.count; l++) {
            int i = l * STRIDE;
            float radius = this.drawn[i + 7];
            var center = modelView.transformPosition(this.drawn[i], this.drawn[i + 1], this.drawn[i + 2], this.center);
            // Looking down -z: a sphere wholly behind the camera lights nothing seen
            if (center.z > radius || !this.screenBounds(center, radius, projection))
                continue;
            int o = this.placedCount * PLACED_STRIDE;
            this.placed[o] = center.x;
            this.placed[o + 1] = center.y;
            this.placed[o + 2] = center.z;
            this.placed[o + 3] = radius;
            System.arraycopy(this.drawn, i + 3, this.placed, o + 4, 4);
            System.arraycopy(this.bounds, 0, this.placed, o + 8, 4);
            this.area[0] = Math.min(this.area[0], this.bounds[0]);
            this.area[1] = Math.min(this.area[1], this.bounds[1]);
            this.area[2] = Math.max(this.area[2], this.bounds[2]);
            this.area[3] = Math.max(this.area[3], this.bounds[3]);
            this.placedCount++;
        }
    }

    /**
     * The part of the screen, in normalized device coordinates (-1 to 1), a light can light, into {@link #bounds}: a
     * square facing the camera that covers its sphere, or the whole screen when the camera is in or right next to it
     * (or the square reaches behind the camera). False if it is wholly off screen.
     */
    private boolean screenBounds(Vector3f center, float radius, Matrix4fc projection) {
        float dist = center.length();
        float x0 = -1F, y0 = -1F, x1 = 1F, y1 = 1F;
        if (dist >= radius * NEAR_FULL_SCREEN) {
            var dir = this.dir.set(center).div(dist);
            var up = Math.abs(dir.y) < 0.99F ? this.up.set(0F, 1F, 0F) : this.up.set(1F, 0F, 0F);
            var right = this.right.set(dir).cross(up).normalize();
            up.set(right).cross(dir);
            float half = radius * dist / (float) Math.sqrt(dist * dist - radius * radius);
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            boolean behind = false;
            for (int k = 0; k < 4 && !behind; k++) {
                float sx = (k & 1) == 0 ? -half : half;
                float sy = (k & 2) == 0 ? -half : half;
                this.corner.set(center.x + right.x * sx + up.x * sy, center.y + right.y * sx + up.y * sy, center.z + right.z * sx + up.z * sy, 1F);
                projection.transform(this.corner);
                if (this.corner.w <= 1e-6F) {
                    behind = true;
                } else {
                    float nx = this.corner.x / this.corner.w, ny = this.corner.y / this.corner.w;
                    minX = Math.min(minX, nx);
                    minY = Math.min(minY, ny);
                    maxX = Math.max(maxX, nx);
                    maxY = Math.max(maxY, ny);
                }
            }
            if (!behind) {
                if (maxX < -1F || minX > 1F || maxY < -1F || minY > 1F)
                    return false;
                x0 = Math.max(minX, -1F);
                y0 = Math.max(minY, -1F);
                x1 = Math.min(maxX, 1F);
                y1 = Math.min(maxY, 1F);
            }
        }
        this.bounds[0] = x0;
        this.bounds[1] = y0;
        this.bounds[2] = x1;
        this.bounds[3] = y1;
        return true;
    }

    /**
     * How many lights {@link #place} put on screen
     */
    public int placedCount() {
        return this.placedCount;
    }

    /**
     * Value {@code k} (see {@link #PLACED_STRIDE}) of placed light {@code l}
     */
    public float placed(int l, int k) {
        return this.placed[l * PLACED_STRIDE + k];
    }

    /**
     * Value {@code k} of the part of the screen the placed lights can light between them: x0, y0, x1, y1, in
     * normalized device coordinates
     */
    public float area(int k) {
        return this.area[k];
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

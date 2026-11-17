// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.groundfog;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.orecruncher.dsurround.config.GroundFogQuality;

/**
 * The values the ground fog's passes share for a frame ({@code GroundFogInfo}, shaders/include/ground_fog.glsl), and
 * how the grid is laid out in its texture, worked out with no GPU in sight: {@link GroundFogVolume} only packs them into
 * its uniform block (the matrix, then each four of {@code vec4s} in turn) and uploads the texels.
 * <p>
 * Each vec3 of the block and the float after it are one vector here, and each vec2 and the two floats after it: in
 * std140 the float takes the vec3's fourth slot, but Std140Builder.putVec3 always takes sixteen bytes, which would put
 * everything after it out by four.
 *
 * @param invViewProj turns a pixel's place on the screen and its depth back into a place, from the camera
 * @param vec4s       the rest, four floats to a vector
 */
public record GroundFogFrame(Matrix4fc invViewProj, float[] vec4s) {

    /**
     * Vectors after the matrix
     */
    static final int VECTORS = 7;

    // Where some of the values are, as indexes into vec4s, for tests
    static final int CAMERA = 12;
    static final int NOISE_ORIGIN = 16;
    static final int TOP = 19;
    static final int FADE = 24;
    static final int THICKNESS = 26;

    /**
     * @param camX       where the camera is, in the world
     * @param baseY      the grid's base: heights in its texture are from here
     * @param highest    the highest ground under any mist (see {@link #texel})
     * @param projection the frame's projection, view bobbing in it (see {@code LevelProjection})
     * @param modelView  the camera's rotation
     * @param width      the size in pixels of the scene the mist is laid over
     * @param strength   how thick the mist is, 0 to 1, from the conditions
     * @param driftX     how far the mist's noise has drifted with the wind, in blocks
     * @param seconds    for the noise's slow churn
     * @param zeroToOne  whether the device's depth runs 0 to 1 (rather than -1 to 1)
     */
    public static GroundFogFrame of(GroundFogField field, double camX, double camY, double camZ, int baseY, float highest,
                                    Matrix4fc projection, Matrix4fc modelView, int width, int height,
                                    GroundFogQuality quality, float strength, float driftX, float driftZ, float seconds,
                                    boolean zeroToOne) {
        float top = highest + GroundFogVolume.MIST_DEPTH;
        // The mist thins to nothing a block inside the area's edge, from where the camera is: beyond that, the field has
        // no cells (see GroundFogField.prepare)
        float fadeWidth = field.radius() * GroundFogField.EDGE_FADE;
        var invViewProj = new Matrix4f(projection).mul(modelView).invert();
        var invProj = new Matrix4f(projection).invert();

        var v = new float[VECTORS * 4];
        put(v, 0, invProj.m02(), invProj.m12(), invProj.m22(), invProj.m32());
        put(v, 1, invProj.m03(), invProj.m13(), invProj.m23(), invProj.m33());
        put(v, 2, width, height, quality.scale(), quality.maxSteps());
        // The camera, in the grid's terms: a block to a texel, heights from the base; and where the base is in the world,
        // so the noise stays put as the camera goes up and down. Not wrapped: heights are small, and a wrap would shift
        // the noise
        put(v, 3, (float) (camX - field.cellX(0)), (float) (camY - baseY), (float) (camZ - field.cellZ(0)), baseY);
        put(v, 4, Math.floorMod(field.cellX(0), GroundFogVolume.NOISE_WRAP), Math.floorMod(field.cellZ(0), GroundFogVolume.NOISE_WRAP),
                field.size(), top - baseY);
        put(v, 5, driftX, driftZ, seconds, GroundFogVolume.MIST_DEPTH);
        put(v, 6, field.radius() - 1F, fadeWidth, GroundFogVolume.THICKNESS * strength, zeroToOne ? 1F : 0F);
        return new GroundFogFrame(invViewProj, v);
    }

    private static void put(float[] v, int vector, float x, float y, float z, float w) {
        int i = vector * 4;
        v[i] = x;
        v[i + 1] = y;
        v[i + 2] = z;
        v[i + 3] = w;
    }

    /**
     * The lowest the smoothed ground is anywhere in the grid
     */
    static float lowestGround(GroundFogField field) {
        int size = field.size();
        float lowest = Float.POSITIVE_INFINITY;
        for (int j = 0; j < size; j++)
            for (int i = 0; i < size; i++)
                lowest = Math.min(lowest, field.smoothHeight(i, j));
        return lowest;
    }

    /**
     * A column's texel in the grid's texture, as ABGR: red the smoothed ground's height above the base in whole blocks
     * and alpha the part of a block over, green the mist's thickness, blue the block and sky light
     */
    static int texel(GroundFogField field, int i, int j, int baseY) {
        float above = Mth.clamp(field.smoothHeight(i, j) - baseY, 0F, 255.99F);
        int r = Mth.floor(above);
        int a = Mth.clamp(Math.round((above - r) * 255F), 0, 255);
        int g = Mth.clamp(Math.round(field.density(i, j) * 255F), 0, 255);
        int light = field.light(i, j);
        // Block light and sky light, 0 to 15 each, together in a byte
        int b = ((light >> 4) & 0xF) << 4 | ((light >> 20) & 0xF);
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /**
     * The highest ground under any mist in the grid, in blocks; negative infinity if there is no mist
     */
    static float highestUnderMist(GroundFogField field) {
        int size = field.size();
        float highest = Float.NEGATIVE_INFINITY;
        for (int j = 0; j < size; j++)
            for (int i = 0; i < size; i++)
                if (field.density(i, j) > 0F)
                    highest = Math.max(highest, field.smoothHeight(i, j));
        return highest;
    }
}

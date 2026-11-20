// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.halo;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.orecruncher.dsurround.effects.rainbow.RainbowConditions;
import org.orecruncher.dsurround.effects.rainbow.RainbowFrame;

/**
 * The halo's shader values for a frame ({@code HaloInfo}, shaders/include/halo.glsl), worked out with no GPU in sight:
 * {@link HaloRenderer} only packs them into its uniform block, the matrix and then each four of {@code vec4s} in turn.
 *
 * @param invViewProj turns a pixel's place on the screen back into a direction in the sky
 * @param vec4s       the rest, four floats to a vector
 */
public record HaloFrame(Matrix4fc invViewProj, float[] vec4s) {

    /**
     * Vectors after the matrix
     */
    static final int VECTORS = 4;

    // Where some of the values are, as indexes into vec4s, for tests
    static final int SUN = 0;
    static final int STRENGTH = 3;
    static final int SUN_DOGS = 7;
    static final int DOG_AZIMUTH = 8;
    static final int ELEVATION = 11;
    static final int BLOCKY = 12;

    /**
     * How bright the brightest of the halo and the sun dogs is added to the sky, at full strength, before the sun's
     * color
     */
    static final float BRIGHTNESS = 0.85F;

    /**
     * @param celestialAngle the sun's celestial angle in degrees (noon 0, midnight 180)
     * @param strength       how strongly the halo shows, 0 to 1
     * @param sunDogs        how strongly the sun dogs show, 0 to 1
     * @param projection     the frame's projection (see {@code LevelProjection})
     * @param viewMatrix     the camera's rotation, from the sky rendering
     * @param blocky         whether it is drawn in blocks (Sky style Blocky)
     */
    public static HaloFrame of(float celestialAngle, float strength, float sunDogs, Matrix4fc projection, Matrix4fc viewMatrix,
                               boolean blocky) {
        var invViewProj = new Matrix4f(projection).mul(viewMatrix).invert();
        // The sun is opposite the point a rainbow is centered on
        var sun = RainbowFrame.antisolar(celestialAngle).negate();
        float elevation = RainbowConditions.sunElevation(celestialAngle);
        var tint = RainbowFrame.sunlight(elevation);

        var v = new float[VECTORS * 4];
        put(v, 0, sun.x, sun.y, sun.z, Math.clamp(strength, 0F, 1F));
        put(v, 1, tint.x * BRIGHTNESS, tint.y * BRIGHTNESS, tint.z * BRIGHTNESS, Math.clamp(sunDogs, 0F, 1F));
        // Where the sun dogs are for red, green and blue light: none (NaN) is sent as far off, where nothing is drawn
        put(v, 2, dog(elevation, 0), dog(elevation, 1), dog(elevation, 2), elevation);
        put(v, 3, blocky ? 1F : 0F, 0F, 0F, 0F);
        return new HaloFrame(invViewProj, v);
    }

    private static float dog(float elevation, int channel) {
        float azimuth = HaloConditions.sunDogAzimuth(elevation, HaloConditions.ICE[channel]);
        return Float.isNaN(azimuth) ? 180F : azimuth;
    }

    private static void put(float[] v, int vector, float x, float y, float z, float w) {
        int i = vector * 4;
        v[i] = x;
        v[i + 1] = y;
        v[i + 2] = z;
        v[i + 3] = w;
    }
}

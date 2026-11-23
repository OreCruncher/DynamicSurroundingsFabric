// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.rainbow;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.orecruncher.dsurround.effects.FrameBlock;

import static org.orecruncher.dsurround.effects.FrameBlock.put;

/**
 * The rainbow's shader values for a frame ({@code RainbowInfo}, shaders/include/rainbow.glsl), worked out with no GPU
 * in sight: {@link RainbowRenderer} only packs them into its uniform block, the matrix and then each four of
 * {@code vec4s} in turn.
 *
 * @param invViewProj turns a pixel's place on the screen back into a direction in the sky
 * @param vec4s       the rest, four floats to a vector
 */
public record RainbowFrame(Matrix4fc invViewProj, float[] vec4s) implements FrameBlock.Frame {

    /**
     * Vectors after the matrix
     */
    public static final int VECTORS = 4;

    // Where some of the values are, as indexes into vec4s, for tests
    static final int ANTISOLAR = 0;
    static final int STRENGTH = 3;
    static final int ACROSS = 4;
    static final int SECONDARY = 11;
    static final int BLOCKY = 13;
    static final int PALE = 14;

    /**
     * How bright the brightest of the bow is added to the sky, at full strength, before the sun's color
     */
    static final float BRIGHTNESS = 0.65F;
    /**
     * How bright a moonbow is next to a rainbow, and how pale: moonlight is far dimmer than sunlight, but the night sky
     * behind it is dark, and at night eyes see little color, so one looks nearly white
     */
    static final float MOONBOW_BRIGHTNESS = 0.6F;
    static final float MOONBOW_PALE = 0.75F;
    /**
     * Moonlight's color: sunlight off the moon, a little cooler to the eye at night
     */
    static final Vector3f MOONLIGHT = new Vector3f(0.8F, 0.87F, 1F);

    /**
     * @param celestialAngle the sun's celestial angle in degrees (noon 0, midnight 180)
     * @param strength       how strongly it shows, 0 to 1
     * @param secondary      how strongly the secondary bow shows, 0 to 1
     * @param projection     the frame's projection (see {@code LevelProjection})
     * @param viewMatrix     the camera's rotation, from the sky rendering
     * @param time           seconds, for the drifting of the patches
     * @param seed           where the bow's patches are, the same for everyone seeing it
     * @param blocky         whether it is drawn as flat stripes in blocks rather than its real colors
     * @param moonbow        whether it is a moonbow, opposite the moon (which is opposite the sun), in moonlight
     */
    public static RainbowFrame of(float celestialAngle, float strength, float secondary, Matrix4fc projection,
                                  Matrix4fc viewMatrix, float time, float seed, boolean blocky, boolean moonbow) {
        var invViewProj = new Matrix4f(projection).mul(viewMatrix).invert();
        // The light's own angle: the moon's is half a turn from the sun's
        float lightAngle = moonbow ? celestialAngle + 180F : celestialAngle;
        var antisolar = antisolar(lightAngle);
        var across = across(antisolar);
        // A low moon reddens as a low sun does
        var tint = sunlight(RainbowConditions.sunElevation(lightAngle));
        if (moonbow)
            tint.mul(MOONLIGHT).mul(MOONBOW_BRIGHTNESS);

        var v = new float[VECTORS * 4];
        put(v, 0, antisolar.x, antisolar.y, antisolar.z, Math.clamp(strength, 0F, 1F));
        put(v, 1, across.x, across.y, across.z, time);
        put(v, 2, tint.x * BRIGHTNESS, tint.y * BRIGHTNESS, tint.z * BRIGHTNESS, Math.clamp(secondary, 0F, 1F));
        put(v, 3, seed, blocky ? 1F : 0F, moonbow ? MOONBOW_PALE : 0F, 0F);
        return new RainbowFrame(invViewProj, v);
    }

    /**
     * The direction of the point opposite the sun, from its celestial angle in degrees. The sun goes east to west
     * overhead (east is +x), as the game draws it: up, turned about the east-west line by the angle.
     */
    public static Vector3f antisolar(float celestialAngle) {
        double angle = Math.toRadians(celestialAngle);
        // The sun is at (-sin, cos, 0)
        return new Vector3f((float) Math.sin(angle), (float) -Math.cos(angle), 0F);
    }

    /**
     * A direction square to the point opposite the sun, as near straight up as can be: where the top of the bow is,
     * from which the shader measures around it
     */
    public static Vector3f across(Vector3f antisolar) {
        var up = new Vector3f(0F, 1F, 0F);
        var across = up.sub(new Vector3f(antisolar).mul(antisolar.y));
        if (across.lengthSquared() < 1.0E-6F)
            return new Vector3f(1F, 0F, 0F);
        return across.normalize();
    }

    /**
     * The color of sunlight from a sun this high, in degrees: white well up, yellower and then redder as it nears the
     * horizon, its light passing through more air; a bow at sunset is mostly red
     */
    public static Vector3f sunlight(float elevation) {
        float t = Math.clamp(elevation / 15F, 0F, 1F);
        return new Vector3f(1F, 0.55F + 0.45F * t, 0.3F + 0.7F * t * t);
    }
}

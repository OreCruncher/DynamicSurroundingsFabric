// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.aurora;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.orecruncher.dsurround.effects.FrameBlock;

import static org.orecruncher.dsurround.effects.FrameBlock.put;

/**
 * The cinematic aurora's shader values for a frame ({@code CinematicAuroraInfo}, shaders/include/aurora_cinematic.glsl),
 * worked out with no GPU in sight: {@link CinematicAuroraRenderer} only packs them into its uniform block, the matrix and
 * then each four of {@code vec4s} in turn. They are laid out as the block has them: {@link #HEADER} vectors, then for
 * each of the four ribbon values in turn a vector per ribbon, up to {@link CinematicAurora#MAX_RIBBONS}, with values that
 * draw nothing for ribbons there aren't.
 *
 * @param invViewProj turns a pixel's place on the screen back into a direction in the sky
 * @param vec4s       the rest, four floats to a vector
 */
public record CinematicAuroraFrame(Matrix4fc invViewProj, float[] vec4s) implements FrameBlock.Frame {

    /**
     * Vectors before the ribbons'
     */
    static final int HEADER = 3;
    /**
     * Vectors in all
     */
    static final int VECTORS = HEADER + CinematicAurora.MAX_RIBBONS * 5;

    // Where some of the values are, as indexes into vec4s, for tests
    static final int ALPHA = 1;
    static final int PIXEL_ANGLE = 2;
    static final int RIBBON_COUNT = 3;
    static final int VIOLET = 5;
    static final int RIBBON_AXES = HEADER * 4;

    /**
     * How strong the violet toward the ribbons' tops is on a calm night, and how much more at the height of a breakup
     */
    static final float BASE_VIOLET = 0.5F;
    static final float SURGE_VIOLET = 0.5F;
    /**
     * How far the night's activity is flattened (see {@link #activityGain}): 1 not at all, 0 entirely
     */
    static final float ACTIVITY_CURVE = 0.35F;

    /**
     * How much brighter a ribbon is for the night's activity: the realistic aurora's gain, flattened (to the power
     * {@link #ACTIVITY_CURVE}), so a storm stays restrained and an ordinary night still shows. With the gain itself a
     * storm was several times an ordinary night, and an exposure that kept the storm from glaring left the ordinary night
     * barely there; with its square root, still too faint
     */
    static float activityGain(float gain) {
        return (float) Math.pow(Math.max(gain, 0F), ACTIVITY_CURVE);
    }

    /**
     * @param moment     how the night is (its realistic aurora's moment: how active)
     * @param motion     where the ribbons' folds, wave and rays have moved to
     * @param alpha      how strongly, 0 to 1
     * @param projection the frame's projection (see {@code LevelProjection})
     * @param viewMatrix the camera's rotation, from the sky rendering
     * @param time       seconds, for the animation
     * @param height     the height in pixels of what it is drawn to
     */
    public static CinematicAuroraFrame of(CinematicAurora aurora, RealisticAurora.Moment moment, CinematicAuroraMotion motion,
                                          float alpha, Matrix4fc projection, Matrix4fc viewMatrix, float time, int height) {
        var ribbons = aurora.ribbons();
        int count = Math.min(ribbons.size(), CinematicAurora.MAX_RIBBONS);
        var invViewProj = new Matrix4f(projection).mul(viewMatrix).invert();
        float pixelAngle = 2F / (projection.m11() * height);
        float surge = Math.clamp(moment.surge(), 0F, 1F);

        var v = new float[VECTORS * 4];
        put(v, 0, time, Math.clamp(alpha, 0F, 1F), pixelAngle, count);
        put(v, 1, moment.rayContrast(), BASE_VIOLET + SURGE_VIOLET * surge, 0F, 0F);
        put(v, 2, CinematicAurora.lowerEdgeAt(surge), 0F, 0F, 0F);

        int max = CinematicAurora.MAX_RIBBONS;
        for (int i = 0; i < max; i++) {
            if (i < count) {
                var ribbon = ribbons.get(i);
                put(v, HEADER + i, (float) Math.cos(ribbon.heading()), (float) Math.sin(ribbon.heading()),
                        CinematicAurora.distanceAt(ribbon, surge), ribbon.bow());
                put(v, HEADER + max + i, ribbon.foldAmplitude(), ribbon.foldWavelength(), motion.fold(i), ribbon.waveAmplitude());
                put(v, HEADER + 2 * max + i, ribbon.waveWavelength(), motion.wave(i), motion.ray(i), ribbon.thickness());
                put(v, HEADER + 3 * max + i, ribbon.brightness() * activityGain(moment.gain()), ribbon.height(), ribbon.raySharpness(),
                        ribbon.rayOffset());
                // The curl's middle on the ground, from where it is on the ribbon's line now (folds, wave and all)
                var curl = ribbon.curl();
                float cos = (float) Math.cos(ribbon.heading());
                float sin = (float) Math.sin(ribbon.heading());
                float across = ribbon.centerAt(curl.along(), CinematicAurora.distanceAt(ribbon, surge), motion.fold(i), motion.wave(i))
                        + curl.offset();
                put(v, HEADER + 4 * max + i, curl.along() * cos - across * sin, curl.along() * sin + across * cos,
                        curl.radius(), curl.twistAt(motion.curl(i), surge));
            } else {
                put(v, HEADER + i, 1F, 0F, 0F, 0F);
                put(v, HEADER + max + i, 0F, 1F, 0F, 0F);
                put(v, HEADER + 2 * max + i, 1F, 0F, 0F, 1F);
                put(v, HEADER + 3 * max + i, 0F, 1F, 0F, 0F);
                put(v, HEADER + 4 * max + i, 0F, 0F, 1F, 0F);
            }
        }
        return new CinematicAuroraFrame(invViewProj, v);
    }
}

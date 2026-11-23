// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.aurora;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.orecruncher.dsurround.config.AuroraQuality;
import org.orecruncher.dsurround.effects.FrameBlock;

import static org.orecruncher.dsurround.effects.FrameBlock.put;

/**
 * The realistic aurora's shader values for a frame ({@code AuroraInfo}, shaders/include/aurora_realistic.glsl), worked
 * out with no GPU in sight: {@link RealisticAuroraRenderer} only packs them into its uniform block, the matrix and then
 * each four of {@code vec4s} in turn. They are laid out as the block has them: {@link #HEADER} vectors, then for each
 * of the four arc values in turn a vector per arc, up to {@link RealisticAurora#MAX_ARCS}, with values that draw
 * nothing for arcs there aren't.
 *
 * @param invViewProj turns a pixel's place on the screen back into a direction in the sky
 * @param vec4s       the rest, four floats to a vector
 */
public record RealisticAuroraFrame(Matrix4fc invViewProj, float[] vec4s) implements FrameBlock.Frame {

    /**
     * Vectors before the arcs'
     */
    static final int HEADER = 8;
    /**
     * Vectors in all
     */
    public static final int VECTORS = HEADER + RealisticAurora.MAX_ARCS * 4;

    // Where some of the values are, as indexes into vec4s, for tests
    static final int PIXEL_ANGLE = 7;
    static final int ALPHA = 13;
    static final int ARC_COUNT = 15;
    static final int ARC_POSITIONS = HEADER * 4;

    // The colors of the light, as a photograph shows it: oxygen's green (557.7 nm) and red (630 nm), and nitrogen's
    // pink at the lower edge
    private static final float[] GREEN = {0.25F, 1.0F, 0.45F};
    private static final float[] RED = {1.0F, 0.12F, 0.2F};
    private static final float[] FRINGE = {1.0F, 0.3F, 0.75F};
    // And the blue-violet of nitrogen lit by the sun, at the tops of the rays above the Earth's shadow (427.8 nm)
    private static final float[] SUNLIT = {0.45F, 0.3F, 1.0F};

    /**
     * @param shadow     how high the Earth's shadow is over the viewer, in kilometers
     * @param washout    how much moonlight washes out its faint parts
     * @param alpha      how strongly, 0 to 1
     * @param projection the frame's projection (see {@code LevelProjection})
     * @param viewMatrix the camera's rotation, from the sky rendering
     * @param time       seconds, for the animation
     * @param height     the height in pixels of what it is drawn to
     */
    public static RealisticAuroraFrame of(RealisticAurora aurora, RealisticAurora.Moment moment, RealisticAuroraMotion motion,
                                          AuroraQuality quality, float shadow, float washout, float alpha,
                                          Matrix4fc projection, Matrix4fc viewMatrix, float time, int height) {
        var arcs = aurora.arcs();
        int count = Math.min(arcs.size(), RealisticAurora.MAX_ARCS);
        var invViewProj = new Matrix4f(projection).mul(viewMatrix).invert();
        float pixelAngle = 2F / (projection.m11() * height);

        var v = new float[VECTORS * 4];
        put(v, 0, GREEN[0] * moment.green(), GREEN[1] * moment.green(), GREEN[2] * moment.green(), washout);
        put(v, 1, RED[0], RED[1], RED[2], pixelAngle);
        put(v, 2, FRINGE[0], FRINGE[1], FRINGE[2], quality.fieldSamples());
        put(v, 3, time, Math.clamp(alpha, 0F, 1F), height, count);
        put(v, 4, moment.lowerEdge(), RealisticAurora.FIELD_TILT, moment.red(), moment.fringe());
        put(v, 5, moment.rayContrast(), moment.pulse(), (float) Math.cos(aurora.declination()), (float) Math.sin(aurora.declination()));
        put(v, 6, moment.fieldNorth(), moment.fieldWidth(), moment.rayField() * moment.gain(), motion.field());
        put(v, 7, SUNLIT[0] * RealisticAurora.SUNLIT, SUNLIT[1] * RealisticAurora.SUNLIT, SUNLIT[2] * RealisticAurora.SUNLIT, shadow);

        int max = RealisticAurora.MAX_ARCS;
        for (int i = 0; i < max; i++) {
            if (i < count) {
                var arc = arcs.get(i);
                // Where it is now: as much closer as the aurora has come
                put(v, HEADER + i, arc.distance() - moment.approach(), arc.foldAmplitude(), arc.foldWavelength(), arc.phase());
                put(v, HEADER + max + i, arc.waveAmplitude(), arc.waveWavelength(), motion.wave(i), arc.curlAmplitude());
                put(v, HEADER + 2 * max + i, arc.brightness() * moment.gain(), arc.thickness(), motion.curl(i), motion.ray(i));
                put(v, HEADER + 3 * max + i, arc.bow(), 0F, 0F, 0F);
            } else {
                put(v, HEADER + i, 0F, 0F, 1F, 0F);
                put(v, HEADER + max + i, 0F, 1F, 0F, 0F);
                put(v, HEADER + 2 * max + i, 0F, 1F, 0F, 0F);
                put(v, HEADER + 3 * max + i, 0F, 0F, 0F, 0F);
            }
        }
        return new RealisticAuroraFrame(invViewProj, v);
    }

    /**
     * The view's height of the horizon the shader stops at: pixels looking further down than this are discarded
     * (aurora_realistic.fsh, main), all others are marched
     */
    static final float HORIZON = -0.01F;
    // Points across and down the screen at which marchedShare looks
    static final int SHARE_COLUMNS = 32;
    static final int SHARE_ROWS = 18;

    /**
     * The share of the screen the shader marches, 0 to 1: the pixels whose line of sight is above {@link #HORIZON}, the
     * sky behind the world included (the world covers it after). Looked at on a grid of points, each pixel's direction
     * taken as the shader takes it, between two points along its line. For recordings, set against the GPU's time.
     */
    public double marchedShare() {
        var near = new Vector4f();
        var far = new Vector4f();
        int marched = 0;
        for (int row = 0; row < SHARE_ROWS; row++) {
            float y = (row + 0.5F) / SHARE_ROWS * 2F - 1F;
            for (int column = 0; column < SHARE_COLUMNS; column++) {
                float x = (column + 0.5F) / SHARE_COLUMNS * 2F - 1F;
                this.invViewProj.transform(near.set(x, y, 1F, 1F));
                this.invViewProj.transform(far.set(x, y, 0.5F, 1F));
                float dx = far.x / far.w - near.x / near.w;
                float dy = far.y / far.w - near.y / near.w;
                float dz = far.z / far.w - near.z / near.w;
                float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length > 0F && dy / length >= HORIZON)
                    marched++;
            }
        }
        return marched / (double) (SHARE_ROWS * SHARE_COLUMNS);
    }
}

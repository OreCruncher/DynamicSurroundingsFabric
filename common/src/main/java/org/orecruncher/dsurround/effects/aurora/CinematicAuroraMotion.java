// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.aurora;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Where each cinematic ribbon's folds, wave and rays have moved to, frame by frame. As with
 * {@link RealisticAuroraMotion}, their speeds change through the night, so each frame moves them on by how far they go
 * in the time since the last rather than working it out from the time and a speed, which would leap when the speed
 * changed. Each is kept within the length its pattern repeats at, so it stays precise as a float.
 */
public final class CinematicAuroraMotion {

    /**
     * The length the rays' pattern repeats at along a ribbon, in kilometers: both of the shader's ray noises repeat
     * within it (the noise texture's size of cells of {@link CinematicAuroraRenderer#FINE_RAY} and of
     * {@link CinematicAuroraRenderer#COARSE_RAY} kilometers)
     */
    static final double RAY_WRAP = CinematicAuroraRenderer.NOISE_SIZE * 12D;
    /**
     * The most time moved on in one frame, in seconds: after a pause or a stall they carry on from where they were
     */
    static final float MAX_STEP = 0.25F;

    private final float[] fold = new float[CinematicAurora.MAX_RIBBONS];
    private final float[] wave = new float[CinematicAurora.MAX_RIBBONS];
    private final float[] ray = new float[CinematicAurora.MAX_RIBBONS];
    private final float[] curl = new float[CinematicAurora.MAX_RIBBONS];
    private float lastTime = Float.NaN;
    // The ribbons they are moving for: others start over
    @Nullable
    private List<CinematicAurora.Ribbon> ribbons;

    /**
     * Moves them on to {@code time}
     *
     * @param aurora the aurora being drawn
     * @param speed  how much faster than when calm they move now
     * @param time   seconds
     */
    public void advance(CinematicAurora aurora, float speed, float time) {
        var ribbons = aurora.ribbons();
        int n = Math.min(ribbons.size(), CinematicAurora.MAX_RIBBONS);
        if (ribbons != this.ribbons) {
            this.ribbons = ribbons;
            for (int i = 0; i < n; i++) {
                var ribbon = ribbons.get(i);
                this.fold[i] = wrap(ribbon.foldPhase(), Mth.TWO_PI);
                this.wave[i] = 0F;
                this.ray[i] = wrap(-ribbon.rayOffset(), RAY_WRAP);
                this.curl[i] = wrap(ribbon.curl().phase(), Mth.TWO_PI);
            }
            this.lastTime = time;
        }

        float step = Math.clamp(time - this.lastTime, 0F, MAX_STEP);
        this.lastTime = time;
        for (int i = 0; i < n; i++) {
            var ribbon = ribbons.get(i);
            this.fold[i] = wrap(this.fold[i] + (double) ribbon.foldSpeed() * speed * step, Mth.TWO_PI);
            this.wave[i] = wrap(this.wave[i] + (double) ribbon.waveSpeed() * speed * step, ribbon.waveWavelength());
            this.ray[i] = wrap(this.ray[i] + (double) ribbon.raySpeed() * speed * step, RAY_WRAP);
            this.curl[i] = wrap(this.curl[i] + (double) ribbon.curl().speed() * speed * step, Mth.TWO_PI);
        }
    }

    /**
     * Where along their cycle ribbon i's folds are, in radians, within a turn
     */
    public float fold(int i) {
        return this.fold[i];
    }

    /**
     * How far ribbon i's wave has traveled along it, in kilometers, within its wavelength
     */
    public float wave(int i) {
        return this.wave[i];
    }

    /**
     * How far ribbon i's rays have moved along it, in kilometers, from where its pattern starts, within
     * {@link #RAY_WRAP}
     */
    public float ray(int i) {
        return this.ray[i];
    }

    /**
     * Where ribbon i's curl's breathing is, in radians, within a turn
     */
    public float curl(int i) {
        return this.curl[i];
    }

    private static float wrap(double value, double length) {
        double wrapped = value % length;
        return (float) (wrapped < 0D ? wrapped + length : wrapped);
    }
}

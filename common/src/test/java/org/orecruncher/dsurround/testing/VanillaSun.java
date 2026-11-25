// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.testing;

/**
 * The sun's angle at a time of vanilla's day, as its day timeline gives it, for tests of what follows the sun rather
 * than the clock: 0 to 360 degrees from noon (6000 ticks) to noon, eased by the timeline's cubic Bézier
 * (data/minecraft/timeline/day.json).
 */
public final class VanillaSun {

    private static final double X1 = 0.362, Y1 = 0.241, X2 = 0.638, Y2 = 0.759;

    private VanillaSun() {
    }

    private static double bezier(double t, double p1, double p2) {
        double u = 1 - t;
        return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t;
    }

    /**
     * The sun's angle, in degrees, at a time of vanilla's day, in ticks
     */
    public static float angle(long ticks) {
        double progress = Math.floorMod(ticks - 6000, 24000L) / 24000.0;
        double lo = 0, hi = 1;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            if (bezier(mid, X1, X2) < progress)
                lo = mid;
            else
                hi = mid;
        }
        return (float) (360 * bezier((lo + hi) / 2, Y1, Y2));
    }
}

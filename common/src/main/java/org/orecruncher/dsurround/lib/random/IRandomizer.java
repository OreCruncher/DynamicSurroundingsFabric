package org.orecruncher.dsurround.lib.random;

import net.minecraft.util.RandomSource;

/**
 * Standardized interface that various randomizers are "shaped" in to for compatibility. Having the ability to
 * plug in new randomizers without disrupting the code base is useful.
 */
public interface IRandomizer extends RandomSource {
    /**
     * A whole number from {@code midPoint - range} to {@code midPoint + range}, both included, most likely near the
     * middle and less likely toward the ends (a triangular distribution).
     * <p>
     * Not to be confused with RandomSource's {@code triangle(double mode, double deviation)}, which Java picks
     * instead when either argument is a double.
     *
     * @param range how far either side of the middle; 0 always gives the middle
     */
    default int triangle(int midPoint, int range) {
        if (range < 0)
            throw new IllegalArgumentException("range must not be negative: " + range);
        return midPoint + this.nextInt(range + 1) - this.nextInt(range + 1);
    }

    default float nextFloat(float min, float max) {
        if (min >= max)
            throw new IllegalArgumentException("bound - origin is non-positive");
        return min + this.nextFloat() * (max - min);
    }
}

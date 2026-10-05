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

    /**
     * A number from {@code min} (included) up to {@code max} (not included); {@code min} itself if the two are equal.
     *
     * @throws IllegalArgumentException if {@code min} is more than {@code max}
     */
    default float nextFloat(float min, float max) {
        if (min > max)
            throw new IllegalArgumentException("min (%s) must not be more than max (%s)".formatted(min, max));
        if (min == max)
            return min;
        return min + this.nextFloat() * (max - min);
    }
}

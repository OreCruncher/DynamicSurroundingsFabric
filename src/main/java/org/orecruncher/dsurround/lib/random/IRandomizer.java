package org.orecruncher.dsurround.lib.random;

import net.minecraft.util.RandomSource;

/**
 * Standardized interface that various randomizers are "shaped" in to for compatibility. Having the ability to
 * plug in new randomizers without disrupting the code base is useful.
 */
public interface IRandomizer extends RandomSource {
    default int triangle(int midPoint, int range) {
        return midPoint + this.nextInt(range) - this.nextInt(range);
    }

    default float nextFloat(float min, float max) {
        if (min >= max)
            throw new IllegalArgumentException("bound - origin is non-positive");
        return min + this.nextFloat() * (max - min);
    }
}

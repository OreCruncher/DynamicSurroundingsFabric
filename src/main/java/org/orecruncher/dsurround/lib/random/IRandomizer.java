package org.orecruncher.dsurround.lib.random;

/**
 * Standardized interface that various randomizers are "shaped" in to for compatibility. Having the ability to
 * plug in new randomizers without disrupting the code base is useful.
 */
public interface IRandomizer {
    int nextInt();
    int nextInt(int bound);
    boolean nextBoolean();
    double nextDouble();
    float nextFloat();
    double nextGaussian();
    long nextLong();

    default double triangle(double d, double e) {
        return d + e * (this.nextDouble() - this.nextDouble());
    }

    default int triangle(int midPoint, int range) {
        return midPoint + this.nextInt(range) - this.nextInt(range);
    }

    default int nextInt(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("bound - origin is non positive");
        }
        return min + this.nextInt(max - min);
    }
}

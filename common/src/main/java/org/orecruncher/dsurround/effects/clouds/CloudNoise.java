package org.orecruncher.dsurround.effects.clouds;

import java.util.Arrays;

/**
 * Smooth value noise for laying out the clouds: a random value at each whole point, read smoothly between them, in
 * three dimensions (east-west, north-south and time, so the clouds change shape as they go). The same everywhere for a
 * seed, so every player in a world sees the same clouds.
 * <p>
 * {@link #rank} turns a value of {@link #layered} noise into the share of the sky with less, so "the clouds cover 40%
 * of the sky" can be had by keeping the cells whose rank is 0.6 or more.
 */
public final class CloudNoise {

    // Octaves of layered noise, each half the size of the last and half as strong
    private static final int OCTAVES = 3;
    // The values layered noise takes, sorted, sampled once: for rank()
    private static final int RANK_SAMPLES = 8192;
    private static final float[] SORTED = sortedSamples();

    private CloudNoise() {
    }

    /**
     * Smooth noise, 0 to 1
     */
    public static float value(double x, double y, double z, long seed) {
        long x0 = (long) Math.floor(x);
        long y0 = (long) Math.floor(y);
        long z0 = (long) Math.floor(z);
        float fx = fade((float) (x - x0));
        float fy = fade((float) (y - y0));
        float fz = fade((float) (z - z0));

        float c000 = lattice(x0, y0, z0, seed);
        float c100 = lattice(x0 + 1, y0, z0, seed);
        float c010 = lattice(x0, y0 + 1, z0, seed);
        float c110 = lattice(x0 + 1, y0 + 1, z0, seed);
        float c001 = lattice(x0, y0, z0 + 1, seed);
        float c101 = lattice(x0 + 1, y0, z0 + 1, seed);
        float c011 = lattice(x0, y0 + 1, z0 + 1, seed);
        float c111 = lattice(x0 + 1, y0 + 1, z0 + 1, seed);

        float a = lerp(fx, c000, c100);
        float b = lerp(fx, c010, c110);
        float c = lerp(fx, c001, c101);
        float d = lerp(fx, c011, c111);
        return lerp(fz, lerp(fy, a, b), lerp(fy, c, d));
    }

    /**
     * Noise in octaves, each half the size and half as strong as the last: broad shapes with smaller ones on them.
     * 0 to 1, though mostly near the middle.
     */
    public static float layered(double x, double y, double z, long seed) {
        float total = 0F;
        float weight = 1F;
        float weights = 0F;
        double scale = 1D;
        for (int o = 0; o < OCTAVES; o++) {
            total += value(x * scale, y * scale, z, seed + o * 0x9E3779B9L) * weight;
            weights += weight;
            weight *= 0.5F;
            scale *= 2D;
        }
        return total / weights;
    }

    /**
     * The share of the sky where {@link #layered} noise is below this value, 0 to 1
     */
    public static float rank(float layered) {
        int i = Arrays.binarySearch(SORTED, layered);
        if (i < 0)
            i = -i - 1;
        return i / (float) SORTED.length;
    }

    private static float[] sortedSamples() {
        var samples = new float[RANK_SAMPLES];
        for (int i = 0; i < RANK_SAMPLES; i++) {
            // Spread over many cells of the noise, at points that don't fall on whole numbers
            double x = (i % 128) * 1.37D + 0.11D;
            double y = (i / 128) * 1.61D + 0.29D;
            samples[i] = layered(x, y, i * 0.013D, 0x636C6F7564L);
        }
        Arrays.sort(samples);
        return samples;
    }

    private static float lattice(long x, long y, long z, long seed) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h ^= y * 0xC2B2AE3D27D4EB4FL;
        h ^= z * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }

    private static float fade(float t) {
        return t * t * (3F - 2F * t);
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }
}

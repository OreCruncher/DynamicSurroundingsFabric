package org.orecruncher.dsurround.effects.clouds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the noise the clouds are laid out with.
 */
public class CloudNoiseTests {

    private static final long SEED = 42L;

    @Test
    void theSameEverywhereForASeed() {
        assertEquals(CloudNoise.value(10.3, -7.8, 2.1, SEED), CloudNoise.value(10.3, -7.8, 2.1, SEED));
        assertNotEquals(CloudNoise.value(10.3, -7.8, 2.1, SEED), CloudNoise.value(10.3, -7.8, 2.1, SEED + 1));
    }

    @Test
    void staysInRangeAndIsSmooth() {
        float last = CloudNoise.value(0, 0.5, 0.5, SEED);
        for (int i = 1; i < 2000; i++) {
            float now = CloudNoise.value(i * 0.01, 0.5, 0.5, SEED);
            assertTrue(now >= 0F && now <= 1F, "out of range: " + now);
            assertTrue(Math.abs(now - last) < 0.05F, "a jump at " + i);
            last = now;
        }
    }

    @Test
    void worksFarFromTheMiddle() {
        // Far out and negative: whole numbers on either side of a cell still agree
        float below = CloudNoise.value(-2_500_000.0001, 1_000_000.5, 0, SEED);
        float above = CloudNoise.value(-2_499_999.9999, 1_000_000.5, 0, SEED);
        assertEquals(below, above, 0.01F);
    }

    @Test
    void rankGivesTheShareOfTheSkyBelow() {
        // Over a wide area, the share with a rank of at least 1 - c is about c
        for (float coverage : new float[]{0.1F, 0.3F, 0.6F, 0.9F}) {
            int covered = 0;
            int total = 0;
            for (int z = 0; z < 120; z++)
                for (int x = 0; x < 120; x++) {
                    float rank = CloudNoise.rank(CloudNoise.layered(x * CloudField.LOW_SCALE + 500, z * CloudField.LOW_SCALE - 300, 7.3, SEED));
                    if (rank >= 1F - coverage)
                        covered++;
                    total++;
                }
            float share = covered / (float) total;
            assertEquals(coverage, share, 0.1F, "coverage " + coverage);
        }
        assertEquals(0F, CloudNoise.rank(-1F), 1e-6F);
        assertEquals(1F, CloudNoise.rank(2F), 1e-6F);
    }
}

package org.orecruncher.dsurround.effects.frost;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the frost pattern: crystals that grow before the haze, the same each time, and halved for mipmaps
 */
public class FrostTextureTests {

    private static final FrostTexture.Pattern PATTERN = FrostTexture.generate(FrostTexture.SIZE, 42L);

    @Test
    void theSameSeedGivesTheSamePattern() {
        var again = FrostTexture.generate(FrostTexture.SIZE, 42L);
        assertArrayEquals(PATTERN.growth(), again.growth());
        assertArrayEquals(PATTERN.brightness(), again.brightness());
    }

    @Test
    void everyTexelFrostsSometimeAndIsSomewhatBright() {
        for (int i = 0; i < PATTERN.growth().length; i++) {
            assertTrue(PATTERN.growth()[i] >= 0F && PATTERN.growth()[i] <= 1F);
            assertTrue(PATTERN.brightness()[i] >= FrostTexture.HAZE_BRIGHTNESS && PATTERN.brightness()[i] <= 1F);
        }
    }

    @Test
    void crystalsGrowFirstAndTheHazeFillsInLast() {
        var growth = PATTERN.growth();
        long crystal = 0, early = 0;
        for (float g : growth) {
            if (g < FrostTexture.HAZE_GROWTH)
                crystal++;
            if (g < 0.3F)
                early++;
        }
        // Crystals cover some of it, not most; a few start right away
        assertTrue(crystal > growth.length / 20, "crystals: " + crystal);
        assertTrue(crystal < growth.length / 2, "crystals: " + crystal);
        assertTrue(early > 0);
        // The crystals are the brighter
        float crystalBright = 0F, hazeBright = 0F;
        int crystals = 0, haze = 0;
        for (int i = 0; i < growth.length; i++) {
            if (growth[i] < 0.4F) {
                crystalBright += PATTERN.brightness()[i];
                crystals++;
            } else if (growth[i] >= FrostTexture.HAZE_GROWTH) {
                hazeBright += PATTERN.brightness()[i];
                haze++;
            }
        }
        assertTrue(crystalBright / crystals > hazeBright / haze);
    }

    @Test
    void aMipmapAveragesFourTexels() {
        var half = FrostTexture.half(PATTERN);
        assertEquals(FrostTexture.SIZE / 2, half.size());
        float expected = (PATTERN.growth()[PATTERN.index(2, 4)] + PATTERN.growth()[PATTERN.index(3, 4)]
                + PATTERN.growth()[PATTERN.index(2, 5)] + PATTERN.growth()[PATTERN.index(3, 5)]) / 4F;
        assertEquals(expected, half.growth()[half.index(1, 2)], 1.0E-6F);
        // Down to the smallest the texture keeps
        var level = PATTERN;
        for (int i = 1; i < FrostTexture.LEVELS; i++)
            level = FrostTexture.half(level);
        assertEquals(FrostTexture.SIZE >> (FrostTexture.LEVELS - 1), level.size());
    }

    @Test
    void thePatternRepeats() {
        assertEquals(PATTERN.index(0, 0), PATTERN.index(FrostTexture.SIZE, -FrostTexture.SIZE));
        assertEquals(PATTERN.index(FrostTexture.SIZE - 1, 0), PATTERN.index(-1, 0));
    }
}

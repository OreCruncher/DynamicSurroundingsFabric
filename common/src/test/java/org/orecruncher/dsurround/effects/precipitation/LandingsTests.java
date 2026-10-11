package org.orecruncher.dsurround.effects.precipitation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for where particle rain's splashes go, and which drops make one.
 */
public class LandingsTests {

    // A water source block's surface
    private static final float SOURCE_WATER = 8F / 9F;

    @Test
    void onWaterTheSplashIsAtItsSurface() {
        // Regression: water has no collision shape, which was taken as a full block, putting the splash in the air
        // above the water, where the water ripple didn't replace it
        var top = Landings.surfaceTop(Double.NEGATIVE_INFINITY, SOURCE_WATER);
        assertEquals(SOURCE_WATER, top, 1e-6);
        assertTrue(top < 1D, "the splash must be inside the water's block");
    }

    @Test
    void onBlocksTheSplashIsOnTheirShape() {
        assertEquals(1D, Landings.surfaceTop(1D, 0F));
        assertEquals(0.5D, Landings.surfaceTop(0.5D, 0F), "a bottom slab");
        // A waterlogged bottom slab: the water is higher
        assertEquals(SOURCE_WATER, Landings.surfaceTop(0.5D, SOURCE_WATER), 1e-6);
    }

    @Test
    void withNeitherShapeNorFluidItIsTheTopOfTheBlock() {
        assertEquals(1D, Landings.surfaceTop(Double.NEGATIVE_INFINITY, 0F));
    }

    @Test
    void theSmallestDrizzleDropsDontSplash() {
        assertEquals(0F, Landings.splashChance(0F));
        assertEquals(0F, Landings.splashChance(Landings.NO_SPLASH_BELOW));
        assertEquals(1F, Landings.splashChance(Landings.ALWAYS_SPLASH_FROM));
        assertEquals(1F, Landings.splashChance(1F));

        // Rising steadily in between, with no jump
        var previous = 0F;
        for (float size = Landings.NO_SPLASH_BELOW; size <= Landings.ALWAYS_SPLASH_FROM; size += 0.01F) {
            var chance = Landings.splashChance(size);
            assertTrue(chance >= previous && chance - previous < 0.05F);
            previous = chance;
        }
    }
}

package org.orecruncher.dsurround.effects.lightning;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;

import static org.junit.jupiter.api.Assertions.*;

public class CloudGlowTests {

    @Test
    void aSheetFlashSpreadsPatchesAcrossTheCloud() {
        for (long seed = 0; seed < 100; seed++) {
            var glow = CloudGlow.sheet(100F, 3, 0.9F, Randomizer.create(seed));
            assertTrue(glow.count() >= 4 && glow.count() <= 7, "patches: " + glow.count());
            for (int p = 0; p < glow.count(); p++) {
                float out = (float) Math.sqrt(glow.x(p) * glow.x(p) + glow.z(p) * glow.z(p));
                assertTrue(out <= 100F + 1e-3F);
                assertTrue(Math.abs(glow.y(p)) <= 3F, "not along the cloud base");
                assertTrue(glow.radius(p) >= 25F && glow.radius(p) < 55F);
            }
        }
    }

    @Test
    void eachStrokeLightsOnePatchHottest() {
        for (long seed = 0; seed < 100; seed++) {
            var glow = CloudGlow.sheet(100F, 4, 0.9F, Randomizer.create(seed));
            for (int s = 0; s < glow.strokes(); s++) {
                int hottest = 0;
                for (int p = 0; p < glow.count(); p++) {
                    float w = glow.weight(p, s);
                    assertTrue(w >= CloudGlow.LEAST_LIT * 0.8F - 1e-6F && w <= 1F, "weight " + w);
                    if (w == 1F)
                        hottest++;
                }
                assertTrue(hottest >= 1, "no patch lit fully");
            }
        }
    }

    @Test
    void theLightMovesAboutBetweenStrokes() {
        // Over many flashes, the hottest patch is often a different one from one stroke to the next
        int moved = 0;
        for (long seed = 0; seed < 100; seed++) {
            var glow = CloudGlow.sheet(100F, 2, 0.9F, Randomizer.create(seed));
            for (int p = 0; p < glow.count(); p++)
                if (glow.weight(p, 0) == 1F && glow.weight(p, 1) < 1F) {
                    moved++;
                    break;
                }
        }
        assertTrue(moved > 50, "moved in " + moved);
    }

    @Test
    void aBoltLightsTheCloudWhereItLeavesIt() {
        var glow = CloudGlow.around(new float[]{0F, 0F, 0F, 50F, -5F, 20F}, 20F, 30F, 2, 0.45F, Randomizer.create(1L));
        assertEquals(2, glow.count());
        assertEquals(50F, glow.x(1), 8F);
        assertEquals(-5F, glow.y(1));
        assertEquals(1F, glow.weight(0, 0));
        assertEquals(1F, glow.weight(1, 0));
        assertEquals(0.45F, glow.strength());
    }

    @Test
    void weightsAndCellsMustMatchThePatches() {
        assertThrows(IllegalArgumentException.class, () -> new CloudGlow(new float[]{0F, 0F, 0F, 1F}, new float[]{1F}, new int[0], 1, 1F));
        assertThrows(IllegalArgumentException.class, () -> new CloudGlow(new float[]{0F, 0F, 0F, 1F}, new float[0], new int[]{0}, 1, 1F));
    }
}

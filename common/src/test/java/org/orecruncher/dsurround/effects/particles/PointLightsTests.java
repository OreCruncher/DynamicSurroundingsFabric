package org.orecruncher.dsurround.effects.particles;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for which point lights are drawn when there are many: the nearest, the furthest of those fading out.
 */
public class PointLightsTests {

    // PointLights' shader takes its id from Constants, which needs the game's registries
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // Lights at the given x, along a line, each with its index as its strength to tell them apart
    private static float[] lightsAt(float... xs) {
        var lights = new float[(xs.length + 1) * PointLights.STRIDE];
        for (int l = 0; l < xs.length; l++) {
            int i = l * PointLights.STRIDE;
            lights[i] = xs[l];
            lights[i + 1] = 64F;
            lights[i + 2] = -20F;
            lights[i + 3] = 0.2F;
            lights[i + 4] = 0.9F;
            lights[i + 5] = 0.4F;
            lights[i + 6] = l;
            lights[i + 7] = 1.75F;
        }
        return lights;
    }

    @Test
    void sortsNearestFirstKeepingEachLightWhole() {
        // The camera at x 10: distances 30, 2, 15, 0.5, 8
        var lights = lightsAt(40F, 8F, -5F, 10.5F, 18F);
        PointLights.sortNearest(lights, 5, 10.0, 64.0, -20.0);

        float[] expectedX = {10.5F, 8F, 18F, -5F, 40F};
        float[] expectedIndex = {3F, 1F, 4F, 2F, 0F};
        for (int r = 0; r < 5; r++) {
            int i = r * PointLights.STRIDE;
            assertEquals(expectedX[r], lights[i], 1e-6F, "position of the light ranked " + r);
            assertEquals(expectedIndex[r], lights[i + 6], 1e-6F, "the light ranked " + r);
            assertEquals(0.9F, lights[i + 4], 1e-6F);
            assertEquals(1.75F, lights[i + 7], 1e-6F);
        }
        // Past the lights sorted, untouched
        assertEquals(0F, lights[5 * PointLights.STRIDE + 7]);
    }

    @Test
    void sortsMoreLightsThanItHasRoomForYet() {
        int n = 300;
        var xs = new float[n];
        for (int l = 0; l < n; l++)
            xs[l] = (l * 7919) % n;
        var lights = lightsAt(xs);
        PointLights.sortNearest(lights, n, 0.0, 64.0, -20.0);
        for (int r = 0; r < n; r++)
            assertEquals(r, lights[r * PointLights.STRIDE], 1e-6F);
    }

    @Test
    void theNearestAreDrawnInFullAndTheRestFadeOut() {
        assertEquals(1F, PointLights.rankFade(0));
        assertEquals(1F, PointLights.rankFade(PointLights.FADE_FROM - 1));
        assertEquals(0F, PointLights.rankFade(PointLights.MAX_DRAWN));
        assertEquals(0F, PointLights.rankFade(PointLights.MAX_DRAWN + 100));
        float previous = 1F;
        for (int rank = PointLights.FADE_FROM; rank <= PointLights.MAX_DRAWN; rank++) {
            float fade = PointLights.rankFade(rank);
            assertTrue(fade <= previous, "brighter further down at " + rank);
            assertTrue(previous - fade <= 1F / (PointLights.MAX_DRAWN - PointLights.FADE_FROM) + 1e-6F,
                    "jumps at " + rank);
            previous = fade;
        }
    }
}

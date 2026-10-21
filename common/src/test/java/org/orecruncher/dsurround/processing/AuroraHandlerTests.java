package org.orecruncher.dsurround.processing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how fog hides the aurora.
 */
public class AuroraHandlerTests {

    private static final float RENDER_DISTANCE = 192F;

    @Test
    void clearSkiesShowItAll() {
        assertEquals(1F, AuroraHandler.fogVisibility(RENDER_DISTANCE, RENDER_DISTANCE), "the usual sky fog");
        assertEquals(1F, AuroraHandler.fogVisibility(RENDER_DISTANCE * AuroraHandler.FOG_CLEAR_AT, RENDER_DISTANCE), 1e-6F);
    }

    @Test
    void thickeningFogFadesItOut() {
        float previous = 1F;
        for (float share = AuroraHandler.FOG_CLEAR_AT; share >= AuroraHandler.FOG_HIDES_AT; share -= 0.05F) {
            float shown = AuroraHandler.fogVisibility(RENDER_DISTANCE * share, RENDER_DISTANCE);
            assertTrue(shown <= previous + 1e-6F, "shows more as the fog closes in");
            previous = shown;
        }
        assertEquals(0F, AuroraHandler.fogVisibility(RENDER_DISTANCE * AuroraHandler.FOG_HIDES_AT, RENDER_DISTANCE), 1e-6F);
        assertEquals(0F, AuroraHandler.fogVisibility(10F, RENDER_DISTANCE), "thick fog hides it");
    }

    @Test
    void withNoRenderDistanceToGoByItShows() {
        assertEquals(1F, AuroraHandler.fogVisibility(50F, 0F));
    }
}

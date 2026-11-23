// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.AuroraStyle;
import org.orecruncher.dsurround.effects.aurora.Aurora;
import org.orecruncher.dsurround.effects.aurora.CinematicAurora;
import org.orecruncher.dsurround.effects.aurora.RealisticAurora;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how fog hides the aurora, and which aurora shows and when it has to fade out.
 */
public class AuroraHandlerTests {

    // The aurora renderers' shaders take their ids from Constants, which needs the game's registries
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final float RENDER_DISTANCE = 192F;
    private static final long NIGHT = 11;

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

    @Test
    void eachStyleIsToldApart() {
        var classic = Aurora.create(NIGHT, 3);
        var realistic = RealisticAurora.create(NIGHT, 10);
        var cinematic = CinematicAurora.create(NIGHT);
        assertNull(AuroraHandler.showing(null, null, null));
        assertEquals(AuroraStyle.CLASSIC, AuroraHandler.showing(classic, null, null));
        assertEquals(AuroraStyle.REALISTIC, AuroraHandler.showing(null, null, realistic));
        // A cinematic aurora keeps tonight's realistic one for how active it is: it is still the cinematic showing
        assertEquals(AuroraStyle.CINEMATIC, AuroraHandler.showing(null, cinematic, realistic));
    }

    @Test
    void tonightsAuroraInTheChosenStyleStays() {
        var classic = Aurora.create(NIGHT, 3);
        var realistic = RealisticAurora.create(NIGHT, 10);
        assertFalse(AuroraHandler.stale(AuroraStyle.CLASSIC, AuroraStyle.CLASSIC, classic, 3, null, NIGHT));
        assertFalse(AuroraHandler.stale(AuroraStyle.REALISTIC, AuroraStyle.REALISTIC, null, 3, realistic, NIGHT));
        assertFalse(AuroraHandler.stale(AuroraStyle.CINEMATIC, AuroraStyle.CINEMATIC, null, 3, realistic, NIGHT));
        assertFalse(AuroraHandler.stale(null, AuroraStyle.CLASSIC, null, 3, null, NIGHT), "nothing showing");
    }

    @Test
    void aChangeOfStyleFadesTheOldOneOut() {
        var realistic = RealisticAurora.create(NIGHT, 10);
        var cinematic = CinematicAurora.create(NIGHT);
        // From cinematic to realistic: the realistic aurora kept for the cinematic one doesn't make it the realistic
        var showing = AuroraHandler.showing(null, cinematic, realistic);
        assertTrue(AuroraHandler.stale(showing, AuroraStyle.REALISTIC, null, 3, realistic, NIGHT));
        assertTrue(AuroraHandler.stale(AuroraStyle.REALISTIC, AuroraStyle.CINEMATIC, null, 3, realistic, NIGHT));
        assertTrue(AuroraHandler.stale(AuroraStyle.CLASSIC, AuroraStyle.REALISTIC, Aurora.create(NIGHT, 3), 3, null, NIGHT));
    }

    @Test
    void lastNightsAuroraFadesOut() {
        assertTrue(AuroraHandler.stale(AuroraStyle.CLASSIC, AuroraStyle.CLASSIC, Aurora.create(NIGHT - 1, 3), 3, null, NIGHT));
        var lastNight = RealisticAurora.create(NIGHT - 1, 10);
        assertTrue(AuroraHandler.stale(AuroraStyle.REALISTIC, AuroraStyle.REALISTIC, null, 3, lastNight, NIGHT));
        assertTrue(AuroraHandler.stale(AuroraStyle.CINEMATIC, AuroraStyle.CINEMATIC, null, 3, lastNight, NIGHT));
    }

    @Test
    void moreCurtainsThanAllowedFadeOut() {
        var classic = Aurora.create(NIGHT, 3);
        assertFalse(AuroraHandler.stale(AuroraStyle.CLASSIC, AuroraStyle.CLASSIC, classic, classic.bands(), null, NIGHT));
        assertTrue(AuroraHandler.stale(AuroraStyle.CLASSIC, AuroraStyle.CLASSIC, classic, classic.bands() - 1, null, NIGHT));
    }
}

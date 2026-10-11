package org.orecruncher.dsurround.runtime.weather;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.precipitation.Wind;
import org.orecruncher.dsurround.effects.precipitation.WindAccess;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the wind other effects lean with.
 */
public class SceneWindTests {

    @AfterEach
    void stillAir() {
        SceneWind.clear();
    }

    @Test
    void beforeTheFirstTickTheAirIsStill() {
        SceneWind.clear();
        assertEquals(0F, SceneWind.x(10, 10));
        assertEquals(0F, SceneWind.z(10, 10));
    }

    @Test
    void theWindIsTheWindServicesWind() {
        // Due east, toward positive x
        var wind = new Wind();
        WindAccess.set(wind, 0F, 0.2F);
        SceneWind.update(wind, 0, 0);
        assertEquals(0.2F, SceneWind.x(5, 5), 1e-5F);
        assertEquals(0F, SceneWind.z(5, 5), 1e-5F);
    }

    @Test
    void theWindHasItsOwnDiagnosticsLine() {
        var wind = new Wind();
        WindAccess.set(wind, 0F, 0.2F);
        assertEquals("Wind: 0.20 toward E (90)", WindService.diagnosticText(wind));
    }

    @Test
    void theOpenSkyLetsTheWindIn() {
        assertEquals(1F, SceneWind.exposure(15));
        assertEquals(0F, SceneWind.exposure(8), "in a cave or a building");
        assertEquals(0F, SceneWind.exposure(0));
        var part = SceneWind.exposure(11);
        assertTrue(part > 0F && part < 1F);
    }

    @Test
    void somethingLightIsCarriedAtAboutHalfTheWind() {
        // As a particle moves: pushed, then slowed by its drag, tick after tick
        for (float friction : new float[]{0.92F, 0.96F}) {
            float speed = 0F;
            for (int t = 0; t < 500; t++)
                speed = (speed + SceneWind.push(0.2F, 1F, friction)) * friction;
            assertEquals(0.2F * SceneWind.CARRIED_SHARE, speed, 0.2F * 0.06F, "drag " + friction);
        }
        assertEquals(0F, SceneWind.push(0.2F, 0F, 0.92F), "sheltered: no push");
    }
}

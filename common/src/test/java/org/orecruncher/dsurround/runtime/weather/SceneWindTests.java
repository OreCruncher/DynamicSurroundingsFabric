package org.orecruncher.dsurround.runtime.weather;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void aDriftCatchesTheWindAndGoesNoFurther() {
        // Whatever the particle does with its own speed, the drift alone never outruns the wind's carrying speed
        var carried = 0.2F * SceneWind.CARRIED_SHARE;
        float drift = 0F;
        float last = 0F;
        for (int t = 0; t < 2000; t++) {
            drift = SceneWind.drift(drift, 0.2F, 1F);
            assertTrue(drift >= last && drift <= carried, "tick " + t + ": " + drift);
            last = drift;
        }
        assertEquals(carried, drift, 1e-5F);
    }

    @Test
    void aNewParticleCatchesTheWindAsTheModsSmokeDoes() {
        // The smoke's speed under push and drag, beside a drift, from still air: close all the way
        var friction = 0.93F;
        var carried = 0.2F * SceneWind.CARRIED_SHARE;
        float speed = 0F;
        float drift = 0F;
        for (int t = 1; t <= 60; t++) {
            speed = (speed + SceneWind.push(0.2F, 1F, friction)) * friction;
            drift = SceneWind.drift(drift, 0.2F, 1F);
            assertEquals(speed, drift, carried * 0.1F, "tick " + t);
        }
        assertTrue(SceneWind.drift(0F, 0.2F, 1F) < carried * 0.1F, "not at once");
    }

    @Test
    void aGustEasesInAndOut() {
        var drift = 0.1F * SceneWind.CARRIED_SHARE;
        var gusting = SceneWind.drift(drift, 0.2F, 1F);
        assertTrue(gusting > drift && gusting < 0.2F * SceneWind.CARRIED_SHARE * 0.6F, "a step, not a jump");
        var easing = SceneWind.drift(gusting, 0.1F, 1F);
        assertTrue(easing < gusting && easing > drift, "dies away");
    }

    @Test
    void outOfTheWindADriftDiesAway() {
        float drift = 0.1F;
        for (int t = 0; t < 200; t++)
            drift = SceneWind.drift(drift, 0.2F, 0F);
        assertEquals(0F, drift, 1e-5F);
        assertEquals(0F, SceneWind.drift(0F, 0F, 1F), "still air: none");
    }
}

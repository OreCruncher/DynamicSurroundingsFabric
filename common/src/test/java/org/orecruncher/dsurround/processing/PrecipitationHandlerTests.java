package org.orecruncher.dsurround.processing;

import org.junit.jupiter.api.Test;
import net.minecraft.client.ParticleStatus;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for when particle rain is drawn, and how many drops it holds.
 */
public class PrecipitationHandlerTests {

    @Test
    void particleRainStandsAsideForTheParticleRainMod() {
        assertTrue(PrecipitationHandler.isEnabled(true, false));
        assertFalse(PrecipitationHandler.isEnabled(true, true), "the Particle Rain mod draws the rain");
        assertFalse(PrecipitationHandler.isEnabled(false, false), "turned off");
        assertFalse(PrecipitationHandler.isEnabled(false, true));
    }

    @Test
    void densityFollowsTheOptionAndTheParticleSetting() {
        assertEquals(1F, PrecipitationHandler.density(100, ParticleStatus.ALL));
        assertEquals(0.5F, PrecipitationHandler.density(50, ParticleStatus.ALL));
        assertEquals(0.5F, PrecipitationHandler.density(100, ParticleStatus.DECREASED));
        assertEquals(0.25F, PrecipitationHandler.density(100, ParticleStatus.MINIMAL));
        assertEquals(0.125F, PrecipitationHandler.density(50, ParticleStatus.MINIMAL), 1e-6F);
    }

    @Test
    void aWiderRangeHoldsDropsForItsArea() {
        assertEquals(PrecipitationHandler.MAX_DROPS, PrecipitationHandler.capacityFor(12), "the default range");
        assertEquals(PrecipitationHandler.MAX_DROPS * 4, PrecipitationHandler.capacityFor(24), "twice as far, four times the area");
        assertEquals(Math.round(PrecipitationHandler.MAX_DROPS * (8F / 12F) * (8F / 12F)), PrecipitationHandler.capacityFor(8));
    }
}

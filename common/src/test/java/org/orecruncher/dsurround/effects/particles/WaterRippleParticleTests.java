package org.orecruncher.dsurround.effects.particles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how a water ripple is drawn when seen from under the water.
 */
public class WaterRippleParticleTests {

    @Test
    void aCameraUnderTheRippleSeesItFromBelow() {
        assertTrue(WaterRippleParticle.isSeenFromBelow(60.5, 62.9));
        assertFalse(WaterRippleParticle.isSeenFromBelow(64.0, 62.9), "from above");
    }

    @Test
    void fromBelowItIsDrawnUnderTheSurface() {
        // Regression: seen from under the water, the ripple was culled (it faced up) and, being just above the
        // surface, hidden behind the water's underside. The handler places it 1/9 + 0.1 above the water's height and
        // the particle lowers itself 0.2, leaving it about 0.011 above the surface; from below it must be under it.
        double aboveSurface = (1D / 9D) + 0.1D - 0.2D;
        assertTrue(aboveSurface > 0D, "above the surface, seen from above");
        assertTrue(aboveSurface - WaterRippleParticle.UNDERSIDE_DROP < 0D, "below the surface, seen from below");
    }

    @Test
    void fromBelowItIsDimmer() {
        assertTrue(WaterRippleParticle.UNDERSIDE_BRIGHTNESS < 1F && WaterRippleParticle.UNDERSIDE_BRIGHTNESS > 0F);
        assertTrue(WaterRippleParticle.UNDERSIDE_ALPHA < 1F && WaterRippleParticle.UNDERSIDE_ALPHA > 0F);
    }
}

package org.orecruncher.dsurround.effects.entity;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the flicker of a burning mob's light.
 */
public class BurningLightTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void itFlickersAroundItsStrengthWithinBounds() {
        float lowest = Float.MAX_VALUE, highest = -Float.MAX_VALUE, sum = 0F;
        int samples = 2000;
        for (int i = 0; i < samples; i++) {
            float s = BurningLight.flicker(i * 0.37F, 12345);
            lowest = Math.min(lowest, s);
            highest = Math.max(highest, s);
            sum += s;
        }
        assertTrue(lowest >= BurningLight.STRENGTH - BurningLight.FLICKER - 1.0E-4F);
        assertTrue(highest <= BurningLight.STRENGTH + BurningLight.FLICKER + 1.0E-4F);
        assertTrue(highest - lowest > BurningLight.FLICKER, "it should visibly waver");
        assertEquals(BurningLight.STRENGTH, sum / samples, 0.05F);
        // Never so dim that the point lights skip it, nor brighter than full
        assertTrue(lowest > 0.1F && highest <= 1F);
    }

    @Test
    void aZombiesLightReachesAsFarAsAFlameJets() {
        assertEquals(BurningLight.ZOMBIE_RADIUS, BurningLight.radiusFor(0.6F), 1.0E-5F);
        assertEquals(3.5F, BurningLight.ZOMBIE_RADIUS);
        assertTrue(BurningLight.radiusFor(1.4F) > BurningLight.ZOMBIE_RADIUS, "a bigger body's fire reaches further");
        assertTrue(BurningLight.radiusFor(0.4F) < BurningLight.ZOMBIE_RADIUS);
    }

    @Test
    void aCrowdDoesNotFlickerInStep() {
        int differ = 0;
        for (int t = 0; t < 100; t++)
            if (Math.abs(BurningLight.flicker(t, 1) - BurningLight.flicker(t, 40000)) > 0.05F)
                differ++;
        assertTrue(differ > 50);
    }
}

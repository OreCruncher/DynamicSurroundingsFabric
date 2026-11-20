// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.halo;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.rainbow.RainbowFrame;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests when the sun has a halo and sun dogs, and where they are.
 */
public class HaloTests {

    private static final float EPSILON = 1.0E-4F;

    @Test
    void sunDogsAre22DegreesOutWithTheSunOnTheHorizon() {
        // The 60 degree prism's least deviation for ice
        assertEquals(21.8F, HaloConditions.sunDogAzimuth(0F, 1.31F), 0.1F);
    }

    @Test
    void sunDogsMoveOutAsTheSunClimbsAndAreGoneHigh() {
        float last = 0F;
        for (float elevation = 0F; elevation <= 50F; elevation += 5F) {
            float azimuth = HaloConditions.sunDogAzimuth(elevation, 1.31F);
            assertTrue(azimuth > last, "at " + elevation);
            last = azimuth;
        }
        assertTrue(Float.isNaN(HaloConditions.sunDogAzimuth(70F, 1.31F)), "no light reaches them past about 61 degrees");
    }

    @Test
    void redIsNearestTheSun() {
        // Red light is bent least, so the red edge faces the sun
        for (float elevation : new float[]{0F, 20F, 40F})
            assertTrue(HaloConditions.sunDogAzimuth(elevation, HaloConditions.ICE[0])
                    < HaloConditions.sunDogAzimuth(elevation, HaloConditions.ICE[2]));
    }

    @Test
    void theSunMustBeUp() {
        assertEquals(0F, HaloConditions.sunFactor(-5F));
        assertEquals(0F, HaloConditions.sunFactor(0F));
        assertEquals(0.5F, HaloConditions.sunFactor(1.5F), EPSILON);
        assertEquals(1F, HaloConditions.sunFactor(60F));
    }

    @Test
    void sunDogsFadeAsTheSunClimbs() {
        assertEquals(1F, HaloConditions.sunDogFactor(10F));
        assertEquals(1F, HaloConditions.sunDogFactor(HaloConditions.SUN_DOGS_FADE_FROM));
        float middle = HaloConditions.sunDogFactor((HaloConditions.SUN_DOGS_FADE_FROM + HaloConditions.HIGHEST_SUN_DOGS) / 2F);
        assertTrue(middle > 0F && middle < 1F);
        assertEquals(0F, HaloConditions.sunDogFactor(HaloConditions.HIGHEST_SUN_DOGS));
    }

    @Test
    void everyPlayerSeesTheSameAndTheChanceHolds() {
        int with = 0;
        for (long slot = 0; slot < 4000; slot++) {
            long time = slot * HaloConditions.SLOT_TICKS;
            boolean has = HaloConditions.slotHasHalo(time, 0.25F);
            assertEquals(has, HaloConditions.slotHasHalo(time + HaloConditions.SLOT_TICKS - 1, 0.25F), "the same all slot");
            if (has)
                with++;
        }
        assertEquals(1000, with, 100);
        assertFalse(HaloConditions.slotHasHalo(12345L, 0F));
        assertTrue(HaloConditions.slotHasHalo(12345L, 1F));
    }

    @Test
    void valuesSitWhereTheBlockHasThem() {
        // Sun 20 degrees up in the east (celestial angle 290)
        var v = HaloFrame.of(290F, 1.5F, 0.5F, new Matrix4f(), new Matrix4f(), true).vec4s();
        assertEquals(HaloFrame.VECTORS * 4, v.length);
        var sun = RainbowFrame.antisolar(290F).negate();
        assertEquals(sun.x, v[HaloFrame.SUN], EPSILON);
        assertEquals(sun.y, v[HaloFrame.SUN + 1], EPSILON);
        assertTrue(sun.y > 0F, "the sun is up");
        assertEquals(1F, v[HaloFrame.STRENGTH], "strength clamped");
        assertEquals(0.5F, v[HaloFrame.SUN_DOGS]);
        assertEquals(20F, v[HaloFrame.ELEVATION], 0.01F);
        assertEquals(HaloConditions.sunDogAzimuth(20F, HaloConditions.ICE[0]), v[HaloFrame.DOG_AZIMUTH], EPSILON);
        assertEquals(1F, v[HaloFrame.BLOCKY]);
        // With the sun too high for sun dogs, they are sent as far off
        var high = HaloFrame.of(340F, 1F, 0F, new Matrix4f(), new Matrix4f(), false).vec4s();
        assertEquals(180F, high[HaloFrame.DOG_AZIMUTH]);
        assertEquals(0F, high[HaloFrame.BLOCKY]);
    }

    @Test
    void cloudsSpreadingHideThem() {
        assertEquals(1F, HaloConditions.clearSky(0F));
        assertEquals(1F, HaloConditions.clearSky(HaloConditions.CLEAR_UNTIL));
        float middle = HaloConditions.clearSky((HaloConditions.CLEAR_UNTIL + HaloConditions.CLOUDED_BY) / 2F);
        assertTrue(middle > 0F && middle < 1F);
        assertEquals(0F, HaloConditions.clearSky(HaloConditions.CLOUDED_BY));
        assertEquals(0F, HaloConditions.clearSky(0.97F), "a storm's deck");
    }
}

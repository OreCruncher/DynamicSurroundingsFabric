// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.halo;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.rainbow.RainbowFrame;
import org.orecruncher.dsurround.testing.ShaderSources;

import java.io.IOException;

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
    void slotsKeepToTheMorningsAndAfternoons() {
        // Taken from the day clock, 0 at sunrise: a morning runs from sunrise to noon and an afternoon from noon to
        // sunset, each with its own chance, on every day alike
        final long day = 24000L;
        final long noon = 6000L;
        int changedAtNoon = 0;
        int daysDiffer = 0;
        for (long d = 0; d < 200; d++) {
            long sunrise = d * day;
            boolean morning = HaloConditions.slotHasHalo(sunrise, 0.5F);
            for (long t = sunrise; t < sunrise + noon; t += 250)
                assertEquals(morning, HaloConditions.slotHasHalo(t, 0.5F), "day " + d + ": the same all morning");
            assertEquals(morning, HaloConditions.slotHasHalo(sunrise + noon - 1, 0.5F), "day " + d + ": until noon");
            boolean afternoon = HaloConditions.slotHasHalo(sunrise + noon, 0.5F);
            for (long t = sunrise + noon; t < sunrise + 2 * noon; t += 250)
                assertEquals(afternoon, HaloConditions.slotHasHalo(t, 0.5F), "day " + d + ": the same all afternoon");
            if (morning != afternoon)
                changedAtNoon++;
            if (morning != HaloConditions.slotHasHalo(sunrise + day, 0.5F))
                daysDiffer++;
        }
        assertTrue(changedAtNoon > 50, "an afternoon has its own chance: " + changedAtNoon + " of 200");
        assertTrue(daysDiffer > 50, "each morning has its own chance: " + daysDiffer + " of 200");
        // Before the world's first sunrise (a clock set back), the night before is a slot of its own, not the morning's
        assertEquals(HaloConditions.slotHasHalo(-1, 0.5F), HaloConditions.slotHasHalo(-noon, 0.5F));
    }

    @Test
    void theSunStraightOverheadIsDrawn() throws IOException {
        // At noon with the daylight cycle held (celestial angle 0) the sun has no direction across the ground, and the
        // halo is at its strongest: the shader must not normalize that nothing (0/0 made the whole halo NaN)
        var v = HaloFrame.of(0F, 1F, 1F, new Matrix4f(), new Matrix4f(), false).vec4s();
        assertEquals(0F, v[HaloFrame.SUN], EPSILON);
        assertEquals(0F, v[HaloFrame.SUN + 2], EPSILON);
        assertTrue(HaloConditions.sunFactor(v[HaloFrame.ELEVATION]) > 0F, "a halo shows then");
        var shader = ShaderSources.read("core/halo.fsh");
        assertFalse(shader.contains("normalize(Sun.xz)") || shader.contains("normalize(view.xz)"), "an unguarded direction across the ground");
        assertTrue(shader.contains("direction(Sun.xz,") && shader.contains("direction(vec3(0.0, 1.0, 0.0) - Sun.xyz * Sun.y,"));
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

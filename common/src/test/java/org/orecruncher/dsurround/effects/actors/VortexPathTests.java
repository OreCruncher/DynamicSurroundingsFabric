// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A dust devil's life, worked out ahead from where it starts, its seed and the wind.
 */
public class VortexPathTests {

    @Test
    void itStartsWhereItWasPlanned() {
        for (long seed = 0; seed < 50; seed++) {
            var path = VortexPath.of(VortexPath.DUST_DEVIL, 100.5D, -40.25D, seed, 0F);
            assertEquals(100.5D, path.x(0F), 1e-9D);
            assertEquals(-40.25D, path.z(0F), 1e-9D);
        }
    }

    @Test
    void theSameSeedGivesTheSameDevil() {
        var a = VortexPath.of(VortexPath.DUST_DEVIL, 0D, 0D, 42L, 1F);
        var b = VortexPath.of(VortexPath.DUST_DEVIL, 0D, 0D, 42L, 1F);
        assertEquals(a.lifetime(), b.lifetime());
        assertEquals(a.height(), b.height());
        for (float age = 0F; age < a.lifetime(); age += 37F) {
            assertEquals(a.x(age), b.x(age));
            assertEquals(a.z(age), b.z(age));
            assertEquals(a.strength(age), b.strength(age));
        }
    }

    @Test
    void itTravelsDownwind() {
        for (long seed = 0; seed < 50; seed++) {
            var wind = seed * 0.37F;
            var path = VortexPath.of(VortexPath.DUST_DEVIL, 0D, 0D, seed, wind);
            float end = path.lifetime();
            // Weaving, but further along the wind than across it would take back
            var along = path.x(end) * Mth.cos(wind) + path.z(end) * Mth.sin(wind);
            assertTrue(along > 0D, "seed " + seed + " along " + along);
        }
    }

    @Test
    void itMovesSmoothly() {
        // A puff follows it a tick at a time: no jumps
        for (long seed = 0; seed < 20; seed++) {
            var path = VortexPath.of(VortexPath.DUST_DEVIL, 0D, 0D, seed, 0F);
            for (float age = 1F; age < path.lifetime(); age++)
                assertTrue(Math.hypot(path.x(age) - path.x(age - 1), path.z(age) - path.z(age - 1)) < 0.3D);
        }
    }

    @Test
    void itGrowsAndDiesAway() {
        for (long seed = 0; seed < 50; seed++) {
            var path = VortexPath.of(VortexPath.DUST_DEVIL, 0D, 0D, seed, 0F);
            int life = path.lifetime();
            assertTrue(life >= VortexPath.DUST_DEVIL.minLifetime() && life <= VortexPath.DUST_DEVIL.maxLifetime());
            assertEquals(0F, path.strength(0F));
            assertEquals(0F, path.strength(life));
            assertEquals(0F, path.strength(life + 10F));
            for (float age = 1F; age < life; age++) {
                var strength = path.strength(age);
                assertTrue(strength > 0F && strength <= 1F, "seed " + seed + " age " + age);
            }
            assertTrue(path.strength(life / 2F) > path.strength(10F));
            assertTrue(path.strength(life / 2F) > path.strength(life - 10F));
        }
    }

    @Test
    void itsFunnelOpensOutGoingUp() {
        for (long seed = 0; seed < 20; seed++) {
            var path = VortexPath.of(VortexPath.DUST_DEVIL, 0D, 0D, seed, 0F);
            assertTrue(path.height() >= VortexPath.DUST_DEVIL.minHeight() && path.height() <= VortexPath.DUST_DEVIL.maxHeight());
            assertEquals(VortexPath.DUST_DEVIL.baseRadius(), path.radius(0F), 1e-6F);
            for (float h = 0.5F; h <= path.height(); h += 0.5F)
                assertTrue(path.radius(h) > path.radius(h - 0.5F));
            // No wider above its top
            assertEquals(path.radius(path.height()), path.radius(path.height() + 5F), 1e-6F);
        }
    }

    @Test
    void itsFunnelBendsAfterItsFoot() {
        for (long seed = 0; seed < 20; seed++) {
            var path = VortexPath.of(VortexPath.DUST_DEVIL, 0D, 0D, seed, 0F);
            float age = path.lifetime() / 2F;
            // At the ground the axis is the foot
            assertEquals(path.x(age), path.axisX(0F, age), 1e-9D);
            assertEquals(path.z(age), path.axisZ(0F, age), 1e-9D);
            // The top is off to one side of it
            var top = path.height();
            assertTrue(Math.hypot(path.axisX(top, age) - path.x(age), path.axisZ(top, age) - path.z(age)) > 0.5D, "seed " + seed);
            // And moves smoothly, a tick at a time, at every height
            for (float h = 0F; h <= top; h += 1F)
                for (float a = 1F; a < path.lifetime(); a += 7F)
                    assertTrue(Math.hypot(path.axisX(h, a) - path.axisX(h, a - 1), path.axisZ(h, a) - path.axisZ(h, a - 1)) < 0.4D);
        }
    }

    @Test
    void itTurnsQuickestLowDownAndOneWayOnly() {
        var path = VortexPath.of(VortexPath.DUST_DEVIL, 0D, 0D, 7L, 0F);
        var low = path.spin(path.radius(0F));
        var high = path.spin(path.radius(path.height()));
        assertTrue(Math.abs(low) > Math.abs(high));
        assertEquals(Math.signum(low), Math.signum(high));
    }

    @Test
    void someTurnEachWay() {
        int clockwise = 0;
        for (long seed = 0; seed < 100; seed++)
            if (VortexPath.of(VortexPath.DUST_DEVIL, 0D, 0D, seed, 0F).spin(1F) > 0F)
                clockwise++;
        assertTrue(clockwise > 25 && clockwise < 75, "clockwise " + clockwise);
    }

    @Test
    void itsDustTakesOnTheGroundsColorAndGetsThere() {
        int sand = 0xF7E9A3, redSand = 0xD87F33;
        assertEquals(sand, GroundVortex.blend(sand, sand, GroundVortex.COLOR_BLEND));
        var halfway = GroundVortex.blend(sand, redSand, 0.5F);
        assertTrue(((halfway >> 16) & 0xFF) < 0xF7 && ((halfway >> 16) & 0xFF) > 0xD8);
        // Step by step it reaches the new color, not stopping a step short
        int color = sand, steps = 0;
        while (color != redSand && steps < 1000) {
            color = GroundVortex.blend(color, redSand, GroundVortex.COLOR_BLEND);
            steps++;
        }
        assertEquals(redSand, color);
        // Mostly there in a couple of seconds
        int twoSeconds = sand;
        for (int i = 0; i < 40; i++)
            twoSeconds = GroundVortex.blend(twoSeconds, redSand, GroundVortex.COLOR_BLEND);
        assertTrue(Math.abs(((twoSeconds >> 8) & 0xFF) - 0x7F) < 0.25F * (0xE9 - 0x7F));
    }

    @Test
    void aWaterspoutIsAsTallAsItIsGiven() {
        var path = VortexPath.of(VortexPath.WATERSPOUT, 0D, 0D, 3L, 0F, 129F);
        assertEquals(129F, path.height());
        // Thin all the way up, opening out only near the clouds
        assertTrue(path.radius(path.height() / 2F) < 2F * VortexPath.WATERSPOUT.baseRadius(), "radius " + path.radius(path.height() / 2F));
        assertTrue(path.radius(path.height()) > path.radius(path.height() / 2F));
        // Its top far off to one side of its foot, leaning after it
        float age = path.lifetime() / 2F;
        assertTrue(Math.hypot(path.axisX(path.height(), age) - path.x(age), path.axisZ(path.height(), age) - path.z(age)) > 3D);
    }

    @Test
    void aWaterspoutReachesTheClouds() {
        assertEquals(129F, Waterspout.height(63D, 192));
        assertEquals(Waterspout.MIN_HEIGHT, Waterspout.height(180D, 192));
        assertEquals(Waterspout.MAX_HEIGHT, Waterspout.height(-40D, 300));
    }

    @Test
    void aSnowspoutIsADustDevilsSizeAndQuicker() {
        var snow = VortexPath.SNOWSPOUT;
        var dust = VortexPath.DUST_DEVIL;
        assertTrue(snow.maxHeight() <= dust.maxHeight());
        assertTrue(snow.maxSpeed() > dust.maxSpeed());
        for (long seed = 0; seed < 20; seed++) {
            var path = VortexPath.of(snow, 0D, 0D, seed, 0F);
            assertTrue(path.lifetime() >= snow.minLifetime() && path.lifetime() <= snow.maxLifetime());
            assertTrue(path.height() >= snow.minHeight() && path.height() <= snow.maxHeight());
        }
    }

    @Test
    void aFractionalCountRoundsByTheRoll() {
        assertEquals(2, GroundVortex.count(2.3F, 0.5F));
        assertEquals(3, GroundVortex.count(2.3F, 0.2F));
        assertEquals(0, GroundVortex.count(0F, 0F));
        assertEquals(1, GroundVortex.count(1F, 0.99F));
    }
}

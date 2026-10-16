package org.orecruncher.dsurround.effects.precipitation;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the flecks snowflakes leave where they land: how they melt, and how many are kept.
 */
public class SnowFlecksTests {

    @Test
    void liesWholeThenMelts() {
        int lifetime = 50;
        assertEquals(1F, SnowFlecks.remainingForAge(0F, lifetime));
        assertEquals(1F, SnowFlecks.remainingForAge(lifetime * SnowFlecks.WHOLE_SHARE, lifetime), 1e-5F, "whole for a while");

        var previous = 1F;
        for (int age = (int) (lifetime * SnowFlecks.WHOLE_SHARE) + 1; age < lifetime; age++) {
            var remaining = SnowFlecks.remainingForAge(age, lifetime);
            assertTrue(remaining < previous, "should keep melting");
            assertTrue(remaining > 0F, "gone too soon");
            previous = remaining;
        }
        assertEquals(0F, SnowFlecks.remainingForAge(lifetime, lifetime));
    }

    @Test
    void shrinksAsItMelts() {
        assertEquals(1F, SnowFlecks.sizeFor(1F), 1e-5F);
        assertEquals(SnowFlecks.MELTED_SIZE, SnowFlecks.sizeFor(0F), 1e-5F);
        assertTrue(SnowFlecks.sizeFor(0.5F) < 1F && SnowFlecks.sizeFor(0.5F) > SnowFlecks.MELTED_SIZE);
    }

    @Test
    void flecksMeltAndGo() {
        var flecks = new SnowFlecks(100);
        var random = Randomizer.create(3);
        for (int i = 0; i < 50; i++)
            flecks.add(i, 64, 0, 0.5F, 0.5F, 0xF000F0, random);
        assertEquals(50, flecks.count());

        for (int t = 0; t < SnowFlecks.MIN_LIFETIME - 1; t++)
            flecks.tick();
        assertEquals(50, flecks.count(), "none should have melted yet");

        for (int t = 0; t < SnowFlecks.LIFETIME_RANGE + 1; t++)
            flecks.tick();
        assertEquals(0, flecks.count());
    }

    @Test
    void flecksLieJustAboveTheSurfaceAndKeepTheirFlake() {
        var flecks = new SnowFlecks(100);
        var random = Randomizer.create(5);
        for (int i = 0; i < 100; i++)
            flecks.add(0, 64.5, 0, i / 99F, 0.25F, 0xF000F0, random);
        for (int i = 0; i < flecks.count(); i++) {
            assertTrue(flecks.y[i] > 64.5F && flecks.y[i] < 64.5F + 0.02F, () -> "not just above the surface");
            assertEquals(1F, flecks.turnCos[i] * flecks.turnCos[i] + flecks.turnSin[i] * flecks.turnSin[i], 1e-4F);
            assertEquals(0.25F, flecks.variant[i]);
            assertEquals(0xF000F0, flecks.light[i]);
        }
    }

    @Test
    void aFleckOnASideStandsJustOffIt() {
        var flecks = new SnowFlecks(10);
        var random = Randomizer.create(9);
        flecks.add(10, 70.3, 4.6, SnowFlecks.FACE_WEST, 0.5F, 0.5F, 0, random);
        flecks.add(3.2, 70.3, 8, SnowFlecks.FACE_SOUTH, 0.5F, 0.5F, 0, random);
        // Off the face, the way it looks, by the lift; the height where it hit
        assertTrue(flecks.x[0] < 10F && flecks.x[0] > 10F - 0.02F);
        assertEquals(70.3F, flecks.y[0], 1e-5F);
        assertEquals(4.6F, flecks.z[0], 1e-5F);
        assertEquals(SnowFlecks.FACE_WEST, flecks.face[0]);
        assertTrue(flecks.z[1] > 8F && flecks.z[1] < 8F + 0.02F);
        assertEquals(3.2F, flecks.x[1], 1e-5F);
        assertEquals(SnowFlecks.FACE_SOUTH, flecks.face[1]);
    }

    @Test
    void facesLookTheWayTheyAreNamed() {
        assertEquals(-1, SnowFlecks.normalX(SnowFlecks.FACE_WEST));
        assertEquals(1, SnowFlecks.normalX(SnowFlecks.FACE_EAST));
        assertEquals(-1, SnowFlecks.normalZ(SnowFlecks.FACE_NORTH));
        assertEquals(1, SnowFlecks.normalZ(SnowFlecks.FACE_SOUTH));
        assertEquals(0, SnowFlecks.normalX(SnowFlecks.FACE_TOP));
        assertEquals(0, SnowFlecks.normalZ(SnowFlecks.FACE_TOP));
        assertEquals(0, SnowFlecks.normalZ(SnowFlecks.FACE_EAST));
        assertEquals(0, SnowFlecks.normalX(SnowFlecks.FACE_NORTH));
    }

    @Test
    void aFullSetLeavesOutTheRest() {
        var flecks = new SnowFlecks(10);
        var random = Randomizer.create(7);
        for (int i = 0; i < 20; i++)
            flecks.add(i, 64, 0, 0F, 0F, 0, random);
        assertEquals(10, flecks.count());
        assertEquals(9F, flecks.x[9], "the first ten are the ones kept");
    }

    @Test
    void clearingRemovesThemAll() {
        var flecks = new SnowFlecks(10);
        flecks.add(0, 64, 0, 0F, 0F, 0, Randomizer.create(1));
        flecks.clear();
        assertEquals(0, flecks.count());
    }
}

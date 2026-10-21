package org.orecruncher.dsurround.effects.precipitation;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the damp spots rain leaves on soft ground: how they darken and dry, and how many are kept.
 */
public class DampSpotsTests {

    @Test
    void darkensQuicklyThenDries() {
        int lifetime = 80;
        assertEquals(0F, DampSpots.darknessForAge(0F, lifetime));
        var peak = DampSpots.darknessForAge(DampSpots.DARKEN_TICKS, lifetime);
        assertTrue(peak > 0.9F * DampSpots.PEAK_DARKNESS, () -> "only " + peak + " once darkened");

        var previous = peak;
        for (int age = DampSpots.DARKEN_TICKS + 1; age < lifetime; age++) {
            var darkness = DampSpots.darknessForAge(age, lifetime);
            assertTrue(darkness < previous, "should keep fading as it dries");
            previous = darkness;
        }
        assertEquals(0F, DampSpots.darknessForAge(lifetime, lifetime));
    }

    @Test
    void spotsDryAndGo() {
        var spots = new DampSpots(100);
        var random = Randomizer.create(3);
        for (int i = 0; i < 50; i++)
            spots.add(i, 64, 0, 0F, random);
        assertEquals(50, spots.count());

        for (int t = 0; t < DampSpots.MIN_LIFETIME - 1; t++)
            spots.tick();
        assertEquals(50, spots.count(), "none should have dried yet");

        for (int t = 0; t < DampSpots.LIFETIME_RANGE + 1; t++)
            spots.tick();
        assertEquals(0, spots.count());
    }

    @Test
    void spotsLieJustAboveTheSurface() {
        var spots = new DampSpots(100);
        var random = Randomizer.create(5);
        for (int i = 0; i < 100; i++)
            spots.add(0, 64.5, 0, i / 99F, random);
        float smallest = DampSpots.MIN_HALF_SIZE * (1F - DampSpots.HALF_SIZE_VARIATION);
        float largest = (DampSpots.MIN_HALF_SIZE + DampSpots.HALF_SIZE_PER_SIZE) * (1F + DampSpots.HALF_SIZE_VARIATION);
        for (int i = 0; i < spots.count(); i++) {
            assertTrue(spots.y[i] > 64.5F && spots.y[i] < 64.5F + 0.01F, () -> "not just above the surface");
            float half = (float) Math.hypot(spots.cornerX[i], spots.cornerZ[i]);
            assertTrue(half >= smallest - 1e-5F && half <= largest + 1e-5F);
        }
    }

    @Test
    void biggerDropsLeaveBiggerDarkerLongerLastingSpots() {
        // Many of each, so the random parts average out
        var small = new DampSpots(500);
        var large = new DampSpots(500);
        var random = Randomizer.create(13);
        for (int i = 0; i < 500; i++) {
            small.add(0, 64, 0, 0F, random);
            large.add(0, 64, 0, 1F, random);
        }
        assertTrue(meanHalfSize(large) > 2F * meanHalfSize(small));
        assertTrue(meanLifetime(large) > meanLifetime(small) + DampSpots.LIFETIME_PER_SIZE / 2F);

        // The largest drop's spot gets as dark as any; the smallest's a fraction of that
        assertEquals(1F, large.peak[0]);
        assertEquals(DampSpots.SMALL_DROP_DARKNESS, small.peak[0]);
    }

    private static float meanHalfSize(DampSpots spots) {
        float total = 0F;
        for (int i = 0; i < spots.count(); i++)
            total += (float) Math.hypot(spots.cornerX[i], spots.cornerZ[i]);
        return total / spots.count();
    }

    private static float meanLifetime(DampSpots spots) {
        float total = 0F;
        for (int i = 0; i < spots.count(); i++)
            total += spots.lifetime[i];
        return total / spots.count();
    }

    @Test
    void aFullFieldLeavesNewSpotsOut() {
        var spots = new DampSpots(10);
        var random = Randomizer.create(7);
        for (int i = 0; i < 25; i++)
            spots.add(i, 64, 0, 0.5F, random);
        assertEquals(10, spots.count());
    }
}

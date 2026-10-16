package org.orecruncher.dsurround.effects.precipitation;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Far from the world's middle a float can't hold a position finely enough: at 8,000,000 blocks it moves in half
 * blocks, and what is drawn there would snap and jitter. What lands (damp spots, snow flecks, drops on windows) keeps
 * where it is east-west and north-south as a double, and is only made a float once the camera is taken away.
 */
public class FarFromOriginTests {

    private static final double FAR = 8_000_000D;
    // A quarter of a block over: lost to a float out there
    private static final double OVER = 0.25D;

    @Test
    void aFloatCantHoldItThere() {
        assertNotEquals(FAR + OVER, (double) (float) (FAR + OVER), "else these tests show nothing");
    }

    @Test
    void dampSpotsKeepWhereTheyAre() {
        var spots = new DampSpots(10);
        spots.add(FAR + OVER, 64, -FAR - OVER, 0.5F, Randomizer.create(1));
        assertEquals(OVER, spots.x[0] - FAR, 1e-9);
        assertEquals(-OVER, spots.z[0] + FAR, 1e-9);
    }

    @Test
    void snowFlecksKeepWhereTheyAre() {
        var flecks = new SnowFlecks(10);
        flecks.add(FAR + OVER, 64, -FAR - OVER, 0.5F, 0.5F, 0xF000F0, Randomizer.create(1));
        assertEquals(OVER, flecks.x[0] - FAR, 1e-9);
        assertEquals(-OVER, flecks.z[0] + FAR, 1e-9);
    }

    @Test
    void dropsOnWindowsKeepWhereTheyAre() {
        var streaks = new WindowStreaks(10);
        // On the top of a block, so nothing lifts it off a side
        streaks.add(FAR + OVER, 70, -FAR - OVER, SnowFlecks.FACE_TOP, 69, false, 0.5F, 0xF000F0, Randomizer.create(1));
        assertEquals(OVER, streaks.x[0] - FAR, 1e-9);
        assertEquals(-OVER, streaks.z[0] + FAR, 1e-9);
    }
}

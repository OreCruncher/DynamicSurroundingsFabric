package org.orecruncher.dsurround.lib.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the chunk range the nearby block entity search covers.
 */
public class LevelCompatTests {

    @Test
    void positiveCoordinates() {
        assertEquals(0, LevelCompat.chunkCoord(0));
        assertEquals(0, LevelCompat.chunkCoord(15.99));
        assertEquals(1, LevelCompat.chunkCoord(16));
    }

    @Test
    void negativeCoordinatesRoundDown() {
        assertEquals(-1, LevelCompat.chunkCoord(-0.01));
        assertEquals(-1, LevelCompat.chunkCoord(-16));
        assertEquals(-2, LevelCompat.chunkCoord(-16.01));
    }

    @Test
    void villageRangeCoversNineChunksAcross() {
        // A 64 block radius from the middle of a chunk reaches 4 chunks either way
        double center = 8.5;
        int min = LevelCompat.chunkCoord(center - 64);
        int max = LevelCompat.chunkCoord(center + 64);

        assertEquals(-4, min);
        assertEquals(4, max);
    }
}

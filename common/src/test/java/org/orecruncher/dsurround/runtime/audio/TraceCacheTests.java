package org.orecruncher.dsurround.runtime.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TraceCacheTests {

    private final Object world = new Object();

    @Test
    void emptyCacheNeverMatches() {
        assertFalse(new TraceCache().matches(this.world, 1L, 2L, 3L));
    }

    @Test
    void matchesWhatWasStored() {
        var cache = new TraceCache();
        cache.update(this.world, 1L, 2L, 3L);

        assertTrue(cache.matches(this.world, 1L, 2L, 3L));
    }

    @Test
    void anyChangeIsAMiss() {
        var cache = new TraceCache();
        cache.update(this.world, 1L, 2L, 3L);

        assertFalse(cache.matches(new Object(), 1L, 2L, 3L), "different world");
        assertFalse(cache.matches(this.world, 9L, 2L, 3L), "sound moved to another block");
        assertFalse(cache.matches(this.world, 1L, 9L, 3L), "player's eyes moved to another block");
        assertFalse(cache.matches(this.world, 1L, 2L, 4L), "the world changed");
    }

    @Test
    void invalidateClearsIt() {
        var cache = new TraceCache();
        cache.update(this.world, 1L, 2L, 3L);
        cache.invalidate();

        assertFalse(cache.matches(this.world, 1L, 2L, 3L));
    }

    @Test
    void updateReplacesThePreviousConditions() {
        var cache = new TraceCache();
        cache.update(this.world, 1L, 2L, 3L);
        cache.update(this.world, 5L, 6L, 7L);

        assertTrue(cache.matches(this.world, 5L, 6L, 7L));
        assertFalse(cache.matches(this.world, 1L, 2L, 3L));
    }

    @Test
    void worldChangesAreCounted() {
        long before = WorldChangeTracker.generation();

        WorldChangeTracker.changed();

        assertEquals(before + 1, WorldChangeTracker.generation());
    }
}

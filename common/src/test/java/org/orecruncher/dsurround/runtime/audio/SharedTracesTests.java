package org.orecruncher.dsurround.runtime.audio;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SharedTracesTests {

    private static final int CHANNELS = 4;
    private static final int BOUNCES = 4;

    private final Object world = new Object();

    private static TraceResult result(float seed) {
        var result = new TraceResult(CHANNELS, BOUNCES);
        result.soundPos = new Vec3(seed, seed + 1, seed + 2);
        result.occlusion = seed;
        for (int i = 0; i < CHANNELS; i++)
            result.sendGains[i] = seed + i;
        for (int i = 0; i < BOUNCES; i++)
            result.bounceTotals[i] = seed * 10 + i;
        result.sharedAirspace = seed * 2;
        result.airspaceChecks = 128;
        return result;
    }

    private static void assertSameResult(TraceResult expected, TraceResult actual) {
        assertEquals(expected.soundPos, actual.soundPos);
        assertEquals(expected.occlusion, actual.occlusion);
        assertArrayEquals(expected.sendGains, actual.sendGains);
        assertArrayEquals(expected.bounceTotals, actual.bounceTotals);
        assertEquals(expected.sharedAirspace, actual.sharedAirspace);
        assertEquals(expected.airspaceChecks, actual.airspaceChecks);
    }

    @Test
    void emptyTableFindsNothing() {
        var shared = new SharedTraces(4, CHANNELS, BOUNCES);
        assertFalse(shared.find(this.world, 1L, 2L, 3L, true, new TraceResult(CHANNELS, BOUNCES)));
    }

    @Test
    void findsAndCopiesWhatWasStored() {
        var shared = new SharedTraces(4, CHANNELS, BOUNCES);
        var stored = result(5F);
        shared.store(this.world, 1L, 2L, 3L, true, stored);

        var found = new TraceResult(CHANNELS, BOUNCES);
        assertTrue(shared.find(this.world, 1L, 2L, 3L, true, found));
        assertSameResult(stored, found);

        // A copy: changing the stored result afterwards doesn't reach what was found
        stored.sendGains[0] = 99F;
        assertNotEquals(99F, found.sendGains[0]);
    }

    @Test
    void anyChangeIsAMiss() {
        var shared = new SharedTraces(4, CHANNELS, BOUNCES);
        shared.store(this.world, 1L, 2L, 3L, true, result(5F));
        var into = new TraceResult(CHANNELS, BOUNCES);

        assertFalse(shared.find(new Object(), 1L, 2L, 3L, true, into), "different world");
        assertFalse(shared.find(this.world, 9L, 2L, 3L, true, into), "sound in another block");
        assertFalse(shared.find(this.world, 1L, 9L, 3L, true, into), "player's eyes in another block");
        assertFalse(shared.find(this.world, 1L, 2L, 4L, true, into), "the world changed");
        assertFalse(shared.find(this.world, 1L, 2L, 3L, false, into), "traced without occlusion");
    }

    @Test
    void picksTheEntryForTheBlock() {
        var shared = new SharedTraces(4, CHANNELS, BOUNCES);
        var first = result(1F);
        var second = result(2F);
        shared.store(this.world, 10L, 2L, 3L, true, first);
        shared.store(this.world, 20L, 2L, 3L, true, second);

        var found = new TraceResult(CHANNELS, BOUNCES);
        assertTrue(shared.find(this.world, 10L, 2L, 3L, true, found));
        assertSameResult(first, found);
        assertTrue(shared.find(this.world, 20L, 2L, 3L, true, found));
        assertSameResult(second, found);
    }

    @Test
    void replacesTheOldestWhenFull() {
        var shared = new SharedTraces(2, CHANNELS, BOUNCES);
        shared.store(this.world, 1L, 0L, 3L, true, result(1F));
        shared.store(this.world, 2L, 0L, 3L, true, result(2F));
        shared.store(this.world, 3L, 0L, 3L, true, result(3F));

        var into = new TraceResult(CHANNELS, BOUNCES);
        assertFalse(shared.find(this.world, 1L, 0L, 3L, true, into));
        assertTrue(shared.find(this.world, 2L, 0L, 3L, true, into));
        assertTrue(shared.find(this.world, 3L, 0L, 3L, true, into));
    }

    @Test
    void aNewerGenerationDropsOlderEntries() {
        var shared = new SharedTraces(4, CHANNELS, BOUNCES);
        shared.store(this.world, 1L, 2L, 3L, true, result(1F));
        shared.store(this.world, 5L, 2L, 4L, true, result(2F));

        var into = new TraceResult(CHANNELS, BOUNCES);
        assertFalse(shared.find(this.world, 1L, 2L, 3L, true, into));
        assertTrue(shared.find(this.world, 5L, 2L, 4L, true, into));
    }

    @Test
    void aResultFromAnOlderGenerationIsNotKept() {
        // A trace that started before a world change finishing after one that started after it
        var shared = new SharedTraces(4, CHANNELS, BOUNCES);
        shared.store(this.world, 1L, 2L, 4L, true, result(1F));
        shared.store(this.world, 5L, 2L, 3L, true, result(2F));

        var into = new TraceResult(CHANNELS, BOUNCES);
        assertFalse(shared.find(this.world, 5L, 2L, 3L, true, into));
        assertTrue(shared.find(this.world, 1L, 2L, 4L, true, into));
    }

    @Test
    void anotherWorldDropsTheEntries() {
        var shared = new SharedTraces(4, CHANNELS, BOUNCES);
        var other = new Object();
        shared.store(this.world, 1L, 2L, 3L, true, result(1F));
        shared.store(other, 1L, 2L, 3L, true, result(2F));

        var into = new TraceResult(CHANNELS, BOUNCES);
        assertFalse(shared.find(this.world, 1L, 2L, 3L, true, into));
        assertTrue(shared.find(other, 1L, 2L, 3L, true, into));
    }
}

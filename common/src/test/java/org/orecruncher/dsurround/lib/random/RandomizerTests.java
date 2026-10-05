package org.orecruncher.dsurround.lib.random;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class RandomizerTests {

    private static IRandomizer seeded(long seed) {
        return new MinecraftRandomizer(RandomSource.create(seed));
    }

    @Test
    void triangleCoversTheWholeRangeInclusive() {
        var random = seeded(1);
        var seen = new int[7];
        for (int i = 0; i < 20_000; i++) {
            var v = random.triangle(10, 3);
            assertTrue(v >= 7 && v <= 13, "out of range: " + v);
            seen[v - 7]++;
        }
        // Both ends are reached
        assertTrue(seen[0] > 0, "never reached the low end");
        assertTrue(seen[6] > 0, "never reached the high end");
    }

    @Test
    void triangleIsMostLikelyNearTheMiddle() {
        var random = seeded(2);
        var seen = new int[7];
        for (int i = 0; i < 50_000; i++)
            seen[random.triangle(0, 3) + 3]++;
        // A triangle: rising to the middle, falling after
        for (int i = 0; i < 3; i++) {
            assertTrue(seen[i] < seen[i + 1], "not rising toward the middle at " + (i - 3));
            assertTrue(seen[6 - i] < seen[5 - i], "not falling away from the middle at " + (3 - i));
        }
        // Symmetric, within noise: each end is about 1/16 of the draws
        assertEquals(seen[0], seen[6], 50_000 * 0.01);
    }

    @Test
    void triangleWithNoRangeIsTheMiddle() {
        var random = seeded(3);
        for (int i = 0; i < 100; i++)
            assertEquals(42, random.triangle(42, 0));
    }

    @Test
    void triangleRejectsANegativeRange() {
        assertThrows(IllegalArgumentException.class, () -> seeded(4).triangle(0, -1));
    }

    @Test
    void sharedRandomizerCannotBeReseeded() {
        assertThrows(UnsupportedOperationException.class, () -> Randomizer.current().setSeed(1L));
    }

    @Test
    void sharedRandomizerWorksFromAnyThread() throws InterruptedException {
        // The same shared instance, kept in a static field, used from another thread
        var shared = Randomizer.current();
        var failure = new AtomicReference<Throwable>();
        var values = new long[2];
        var thread = new Thread(() -> {
            try {
                values[0] = shared.nextLong();
                values[1] = shared.nextLong();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        thread.start();
        thread.join();
        assertNull(failure.get(), () -> "failed on another thread: " + failure.get());
        assertNotEquals(values[0], values[1]);
    }

    @Test
    void eachThreadHasItsOwnSequence() throws InterruptedException {
        // Each thread's generator is seeded on its own, so two threads don't produce the same numbers
        var first = new long[4];
        var second = new long[4];
        var a = new Thread(() -> {
            for (int i = 0; i < first.length; i++)
                first[i] = Randomizer.current().nextLong();
        });
        var b = new Thread(() -> {
            for (int i = 0; i < second.length; i++)
                second[i] = Randomizer.current().nextLong();
        });
        a.start();
        b.start();
        a.join();
        b.join();
        assertFalse(java.util.Arrays.equals(first, second));
    }

    @Test
    void createdFromTheSameSeedGivesTheSameNumbers() {
        var a = Randomizer.create(12345L);
        var b = Randomizer.create(12345L);
        for (int i = 0; i < 100; i++) {
            assertEquals(a.nextLong(), b.nextLong());
            assertEquals(a.nextFloat(1F, 2F), b.nextFloat(1F, 2F));
            assertEquals(a.triangle(0, 5), b.triangle(0, 5));
        }
    }

    @Test
    void createdFromDifferentSeedsDiffer() {
        // Consecutive seeds too: the generator mixes the seed before starting
        var a = Randomizer.create(1L);
        var b = Randomizer.create(2L);
        var same = 0;
        for (int i = 0; i < 100; i++)
            if (a.nextLong() == b.nextLong())
                same++;
        assertEquals(0, same);
    }

    @Test
    void createdRandomizerCanBeReseeded() {
        // It's its own, so unlike the shared one, reseeding is allowed: it starts the sequence again
        var random = Randomizer.create(7L);
        var first = random.nextLong();
        random.nextLong();
        random.setSeed(7L);
        assertEquals(first, random.nextLong());
    }

    @Test
    void createdRandomizersAreIndependent() {
        // Drawing from one, or from the shared randomizer, doesn't disturb another
        var expected = Randomizer.create(99L).nextLong();
        var random = Randomizer.create(99L);
        Randomizer.create(99L).nextLong();
        Randomizer.current().nextLong();
        assertEquals(expected, random.nextLong());
    }

    @Test
    void murmurMixMatchesTheReference() {
        // fmix32 and fmix64 from MurmurHash3, worked out independently
        assertEquals(0, MurmurHash3.hash(0));
        assertEquals(0x514E28B7, MurmurHash3.hash(1));
        assertEquals(0x30F4C306, MurmurHash3.hash(2));
        assertEquals(0x81F16F39, MurmurHash3.hash(-1));
        assertEquals(0L, MurmurHash3.hash(0L));
        assertEquals(0xB456BCFC34C2CB2CL, MurmurHash3.hash(1L));
        assertEquals(0x64B5720B4B825F21L, MurmurHash3.hash(-1L));
    }

    @Test
    void murmurMixScramblesConsecutiveValues() {
        // Consecutive ids give unrelated results: on average about half the bits differ
        long differing = 0;
        for (int i = 0; i < 1000; i++)
            differing += Integer.bitCount(MurmurHash3.hash(i) ^ MurmurHash3.hash(i + 1));
        var average = differing / 1000.0;
        assertEquals(16.0, average, 1.5);
    }
}

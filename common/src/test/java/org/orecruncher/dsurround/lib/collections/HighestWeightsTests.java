package org.orecruncher.dsurround.lib.collections;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class HighestWeightsTests {

    private static Set<Long> kept(HighestWeights weights) {
        var keys = new HashSet<Long>();
        for (int i = 0; i < weights.size(); i++)
            keys.add(weights.key(i));
        return keys;
    }

    @Test
    void keepsEverythingWhileThereIsRoom() {
        var weights = new HighestWeights(4);
        for (long k = 1; k <= 3; k++)
            assertTrue(weights.offer(k, k * 0.1));
        assertEquals(Set.of(1L, 2L, 3L), kept(weights));
    }

    @Test
    void keepsTheHighestWhenFull() {
        var weights = new HighestWeights(3);
        double[] values = {5, 1, 9, 3, 7, 2, 8};
        for (int i = 0; i < values.length; i++)
            weights.offer(i, values[i]);
        // 9, 8 and 7, at indexes 2, 6 and 4
        assertEquals(Set.of(2L, 6L, 4L), kept(weights));
    }

    @Test
    void aTieDoesntDisplaceWhatIsKept() {
        var weights = new HighestWeights(2);
        weights.offer(1, 5);
        weights.offer(2, 5);
        assertFalse(weights.wouldKeep(5));
        assertFalse(weights.offer(3, 5));
        assertEquals(Set.of(1L, 2L), kept(weights));
        assertTrue(weights.wouldKeep(5.0001));
    }

    @Test
    void wouldKeepMatchesOffer() {
        var weights = new HighestWeights(3);
        var random = Randomizer.create(7);
        for (long k = 0; k < 200; k++) {
            var weight = random.nextDouble();
            var expected = weights.wouldKeep(weight);
            assertEquals(expected, weights.offer(k, weight));
        }
    }

    @Test
    void agreesWithSorting() {
        // The kept set is the top N by weight, whatever the order offered
        var random = Randomizer.create(11);
        for (int trial = 0; trial < 50; trial++) {
            var capacity = 1 + random.nextInt(8);
            var count = random.nextInt(40);
            var weights = new HighestWeights(capacity);
            var all = new ArrayList<double[]>();
            for (int i = 0; i < count; i++) {
                // Distinct weights, so the top N is unambiguous
                var weight = random.nextDouble() + i * 1e-9;
                all.add(new double[]{i, weight});
                weights.offer(i, weight);
            }
            all.sort(Comparator.comparingDouble((double[] e) -> e[1]).reversed());
            var expected = new HashSet<Long>();
            for (int i = 0; i < Math.min(capacity, count); i++)
                expected.add((long) all.get(i)[0]);
            assertEquals(expected, kept(weights), "capacity " + capacity + ", " + count + " offered");
        }
    }

    @Test
    void canBeClearedAndReused() {
        var weights = new HighestWeights(2);
        weights.offer(1, 10);
        weights.offer(2, 20);
        weights.clear();
        assertEquals(0, weights.size());
        // Low weights get in again once cleared
        assertTrue(weights.offer(3, 0.5));
        assertEquals(Set.of(3L), kept(weights));
    }

    @Test
    void rejectsBadArguments() {
        assertThrows(IllegalArgumentException.class, () -> new HighestWeights(0));
        var weights = new HighestWeights(2);
        weights.offer(1, 1);
        assertThrows(IndexOutOfBoundsException.class, () -> weights.key(1));
        assertEquals(List.of(1L), List.of(weights.key(0)));
    }
}

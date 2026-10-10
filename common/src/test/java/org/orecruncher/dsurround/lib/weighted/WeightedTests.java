package org.orecruncher.dsurround.lib.weighted;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.random.IRandomizer;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for weights and weighted random selection.
 */
public class WeightedTests {

    /**
     * A randomizer whose nextInt(bound) returns the given values in turn, so a selection can be steered. Fails if a
     * value is out of the bound asked for, or more are asked for than given.
     */
    private static IRandomizer fixed(int... values) {
        var queue = new ArrayDeque<Integer>();
        Arrays.stream(values).forEach(queue::add);
        return (IRandomizer) Proxy.newProxyInstance(IRandomizer.class.getClassLoader(), new Class<?>[]{IRandomizer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("nextInt") && args != null && args.length == 1) {
                        int bound = (int) args[0];
                        int value = queue.remove();
                        assertTrue(value >= 0 && value < bound, value + " is outside nextInt(" + bound + ")");
                        return value;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static <T> WeightTable.Entry<T> entry(T value, int weight) {
        return new WeightTable.Entry<>(value, WeightValue.of(weight));
    }

    // ---- WeightValue -----------------------------------------------------------------------------------------

    @Test
    void weightsFromZeroToTheMaximumAreAllowed() {
        assertEquals(0, WeightValue.of(0).asInt());
        assertEquals(WeightValue.MAX, WeightValue.of(WeightValue.MAX).asInt());
        assertEquals("30", WeightValue.of(30).toString());
    }

    @Test
    void weightsOutsideTheRangeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> WeightValue.of(-1));
        assertThrows(IllegalArgumentException.class, () -> WeightValue.of(WeightValue.MAX + 1));
        assertThrows(IllegalArgumentException.class, () -> new WeightValue(-1), "the record's own constructor checks too");
    }

    @Test
    void codecGivesAnErrorRatherThanThrowing() {
        assertEquals(7, WeightValue.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(7)).getOrThrow().asInt());
        assertTrue(WeightValue.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(-1)).isError());
        assertTrue(WeightValue.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(2_000_000_000)).isError());
    }

    // ---- WeightTable -----------------------------------------------------------------------------------------

    /**
     * A randomizer backed by a seeded generator, for checking proportions over many selections.
     */
    private static IRandomizer seeded(long seed) {
        var random = new SplittableRandom(seed);
        return (IRandomizer) Proxy.newProxyInstance(IRandomizer.class.getClassLoader(), new Class<?>[]{IRandomizer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("nextInt") && args != null && args.length == 1)
                        return random.nextInt((int) args[0]);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * {@code count} entries named "e0", "e1"... with the given weight.
     */
    private static ObjectArray<WeightTable.Entry<String>> entries(int count, int weight) {
        var entries = new ObjectArray<WeightTable.Entry<String>>();
        for (int i = 0; i < count; i++)
            entries.add(entry("e" + i, weight));
        return entries;
    }

    private static Predicate<WeightTable.Entry<String>> named(String... names) {
        var set = Set.of(names);
        return e -> set.contains(e.data());
    }

    @Test
    void selectionIsInProportionToTheWeights() {
        // Weights 1, 3 and 0: positions 0 -> a, 1 to 3 -> b, and c is never chosen
        var entries = new ObjectArray<WeightTable.Entry<String>>();
        entries.add(entry("a", 1));
        entries.add(entry("b", 3));
        entries.add(entry("c", 0));
        var chosen = new ArrayList<String>();
        for (int target = 0; target < 4; target++)
            chosen.add(WeightTable.makeSelection(entries, e -> true, fixed(target)).orElseThrow());

        assertEquals(List.of("a", "b", "b", "b"), chosen);
    }

    @Test
    void allWeightsZeroIsEmptyWithoutARandomNumber() {
        assertEquals(Optional.empty(), WeightTable.makeSelection(entries(3, 0), e -> true, fixed()));
    }

    @Test
    void filteredSelectionChoosesAmongTheMatchesByWeight() {
        // a (1) and c (2) match, b doesn't: positions 0 -> a, 1 and 2 -> c
        var entries = new ObjectArray<WeightTable.Entry<String>>();
        entries.add(entry("a", 1));
        entries.add(entry("b", 5));
        entries.add(entry("c", 2));
        var chosen = new ArrayList<String>();
        for (int target = 0; target < 3; target++)
            chosen.add(WeightTable.makeSelection(entries, named("a", "c"), fixed(target)).orElseThrow());

        assertEquals(List.of("a", "c", "c"), chosen);
    }

    @Test
    void eachEntryIsTestedOnceAndZeroWeightsNotAtAll() {
        // The filter may evaluate a condition script, so it shouldn't run more than needed
        var entries = new ObjectArray<WeightTable.Entry<String>>();
        entries.add(entry("a", 1));
        entries.add(entry("zero", 0));
        entries.add(entry("b", 1));
        var tested = new ArrayList<String>();

        WeightTable.makeSelection(entries, e -> tested.add(e.data()), fixed(1));

        assertEquals(List.of("a", "b"), tested);
    }

    @Test
    void noMatchesIsEmptyWithoutARandomNumber() {
        var entries = entries(5, 1);

        assertEquals(Optional.empty(), WeightTable.makeSelection(entries, e -> false, fixed()));
        assertEquals(Optional.empty(), WeightTable.makeSelection(new ObjectArray<WeightTable.Entry<String>>(), e -> true, fixed()));
    }

    @Test
    void bitmaskCoversAllSixtyFourEntries() {
        // Entry 63 is the long's sign bit
        var entries = entries(64, 1);

        assertEquals(Optional.of("e63"), WeightTable.makeSelection(entries, e -> true, fixed(63)));
        assertEquals(Optional.of("e0"), WeightTable.makeSelection(entries, e -> true, fixed(0)));
        assertEquals(Optional.of("e63"), WeightTable.makeSelection(entries, named("e5", "e63"), fixed(1)));
    }

    @Test
    void moreThanSixtyFourEntriesUseASinglePass() {
        // A random number per match: e3 is kept unless the second draw (bound 2) is below e68's weight of 1
        var entries = entries(70, 1);

        assertEquals(Optional.of("e68"), WeightTable.makeSelection(entries, named("e3", "e68"), fixed(0, 0)));
        assertEquals(Optional.of("e3"), WeightTable.makeSelection(entries, named("e3", "e68"), fixed(0, 1)));
    }

    @Test
    void bothWaysChooseInProportionToTheWeights() {
        // Weights 1 to 4 on the matching entries, so 10%, 20%, 30% and 40%, among entries that don't match
        for (int size : new int[]{10, 70}) {
            var entries = new ObjectArray<WeightTable.Entry<String>>();
            for (int i = 0; i < size; i++)
                entries.add(entry("e" + i, i < 4 ? i + 1 : 3));
            var counts = new int[4];
            var random = seeded(42);
            int draws = 200_000;
            for (int n = 0; n < draws; n++) {
                var chosen = WeightTable.makeSelection(entries, named("e0", "e1", "e2", "e3"), random).orElseThrow();
                counts[Integer.parseInt(chosen.substring(1))]++;
            }
            for (int i = 0; i < 4; i++)
                assertEquals((i + 1) / 10.0, counts[i] / (double) draws, 0.005, size + " entries, e" + i);
        }
    }

    @Test
    void filteredTotalThatOverflowsFailsLoudly() {
        // Only a large collection can overflow: 64 entries at the maximum weight still fit in an int
        var entries = entries(2148, WeightValue.MAX);

        assertThrows(ArithmeticException.class, () -> WeightTable.makeSelection(entries, e -> true, seeded(1)));
    }

    @Test
    void listTotalThatOverflowsFailsLoudly() {
        // Regression: the total wrapped to a negative number and every selection was silently empty
        var builder = WeightedList.<String>builder();
        for (int i = 0; i < 2148; i++)
            builder.add("x", WeightValue.MAX);

        assertThrows(ArithmeticException.class, builder::build);
    }

    @Test
    void entryNeedsAValueAndAWeight() {
        assertThrows(NullPointerException.class, () -> new WeightTable.Entry<>(null, WeightValue.of(1)));
        assertThrows(NullPointerException.class, () -> new WeightTable.Entry<>("a", null));
    }

    // ---- WeightedList ----------------------------------------------------------------------------------------

    @Test
    void listSelectsInProportionToTheWeights() {
        var list = WeightedList.<String>builder().add("a", 2).add("b", 1).build();

        assertEquals(Optional.of("a"), list.getRandomValue(fixed(1)));
        assertEquals(Optional.of("b"), list.getRandomValue(fixed(2)));
    }

    @Test
    void emptyListSelectsNothing() {
        assertEquals(Optional.empty(), WeightedList.<String>builder().build().getRandomValue(fixed()));
        assertEquals(Optional.empty(), WeightedList.<String>builder().add("a", 0).build().getRandomValue(fixed()));
    }

    @Test
    void builderCanBeReused() {
        var builder = WeightedList.<String>builder().add("a", 1);
        var first = builder.build();
        var second = builder.add("b", 1).build();

        assertEquals(Optional.of("a"), first.getRandomValue(fixed(0)));
        assertEquals(Optional.of("b"), second.getRandomValue(fixed(1)), "a later build includes the new entry");
    }
}

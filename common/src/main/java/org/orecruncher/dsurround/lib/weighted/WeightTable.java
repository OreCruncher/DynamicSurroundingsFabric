package org.orecruncher.dsurround.lib.weighted;

import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.random.IRandomizer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Classic WeightTable for random weighted selection.
 */
public class WeightTable {

    // A long has a bit per entry; larger collections use the single-pass selection instead
    private static final int MAX_BITMASK_ENTRIES = Long.SIZE;

    /**
     * Chooses at random among the entries that {@code filter} accepts, in proportion to their weights, without
     * collecting them first: nothing is allocated but the result. Each entry with a weight above 0 is tested once,
     * and entries weighing 0 aren't tested at all, which matters when the filter evaluates a condition script.
     * <p>
     * Up to 64 entries, the matches are recorded in a bitmask, then one random number picks among them. Above that,
     * a single pass picks as it goes (weighted reservoir sampling), drawing a random number per match. Both choose
     * with the same probabilities; the bitmask is faster when several entries match.
     *
     * @return the chosen entry's value, or empty if nothing matches or the matches all weigh 0
     * @throws ArithmeticException if the matching weights add up to more than an int can hold
     */
    public static <T, E extends Entry<T>> Optional<T> makeSelection(final ObjectArray<E> entries, final Predicate<? super E> filter, final IRandomizer randomizer) {
        int count = entries.size();
        if (count <= MAX_BITMASK_ENTRIES)
            return selectByBitmask(entries, count, filter, randomizer);
        return selectInOnePass(entries, count, filter, randomizer);
    }

    private static <T, E extends Entry<T>> Optional<T> selectByBitmask(ObjectArray<E> entries, int count, Predicate<? super E> filter, IRandomizer randomizer) {
        // First pass: test each entry once, remembering the matches and their total weight
        long matched = 0;
        int totalWeight = 0;
        for (int i = 0; i < count; i++) {
            var entry = entries.get(i);
            int weight = entry.weight.asInt();
            if (weight > 0 && filter.test(entry)) {
                matched |= 1L << i;
                totalWeight = Math.addExact(totalWeight, weight);
            }
        }
        if (totalWeight == 0)
            return Optional.empty();

        // Second pass: walk the matches to the randomly chosen point in their total weight
        int target = randomizer.nextInt(totalWeight);
        for (int i = 0; i < count; i++) {
            if ((matched & (1L << i)) != 0) {
                var entry = entries.get(i);
                int weight = entry.weight.asInt();
                if (target < weight)
                    return Optional.of(entry.data);
                target -= weight;
            }
        }
        return Optional.empty(); // not reached: the target is below the total of the matches
    }

    /**
     * Weighted reservoir sampling: each match replaces the choice so far with probability weight / (total so far),
     * which leaves every match chosen in proportion to its weight.
     */
    private static <T, E extends Entry<T>> Optional<T> selectInOnePass(ObjectArray<E> entries, int count, Predicate<? super E> filter, IRandomizer randomizer) {
        T chosen = null;
        int totalWeight = 0;
        for (int i = 0; i < count; i++) {
            var entry = entries.get(i);
            int weight = entry.weight.asInt();
            if (weight > 0 && filter.test(entry)) {
                totalWeight = Math.addExact(totalWeight, weight);
                if (randomizer.nextInt(totalWeight) < weight)
                    chosen = entry.data;
            }
        }
        return Optional.ofNullable(chosen);
    }

    /**
     * Chooses at random among all the entries, in proportion to their weights, given their total from
     * {@link #calculateWeight}. For a fixed list such as {@link WeightedList}, which works out the total once.
     *
     * @return the chosen entry's value, or empty if the total is 0
     */
    static <T> Optional<T> makeSelection(final List<? extends Entry<T>> selections, int totalWeight, IRandomizer randomizer) {
        if (totalWeight <= 0)
            return Optional.empty();

        int target = randomizer.nextInt(totalWeight);
        for (var selection : selections) {
            int weight = selection.weight.asInt();
            if (target < weight)
                return Optional.of(selection.data());
            target -= weight;
        }
        return Optional.empty(); // not reached: the target is below the total
    }

    /**
     * The sum of the weights.
     *
     * @throws ArithmeticException if it is too big for an int. With weights capped at {@link WeightValue#MAX} that
     *                             takes thousands of entries, but it fails loudly rather than wrapping to a negative
     *                             total, which would make every selection silently empty.
     */
    static <T> int calculateWeight(final List<? extends Entry<T>> selections) {
        int totalWeight = 0;
        for (var selection : selections) {
            totalWeight = Math.addExact(totalWeight, selection.weight.asInt());
        }
        return totalWeight;
    }

    /**
     * A value and its weight. The value can't be null, so an empty selection always means nothing was chosen.
     */
    public static class Entry<T> {

        protected final WeightValue weight;
        protected final T data;

        protected Entry(T data, WeightValue weight) {
            this.weight = Objects.requireNonNull(weight, "weight");
            this.data = Objects.requireNonNull(data, "data");
        }

        public WeightValue weight() {
            return this.weight;
        }

        public T data() {
            return this.data;
        }
    }
}

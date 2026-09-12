package org.orecruncher.dsurround.lib.weighted;

import org.orecruncher.dsurround.lib.random.IRandomizer;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Classic WeightTable for random weighted selection.
 */
public class WeightTable {

    public static <T> Optional<T> makeSelection(final Stream<? extends Entry<T>> inputStream, IRandomizer randomizer) {
        var collection = inputStream.toList();
        return makeSelection(collection, randomizer);
    }

    public static <T> Optional<T> makeSelection(final List<? extends Entry<T>> selections, IRandomizer randomizer) {
        if (selections.isEmpty())
            return Optional.empty();

        int totalWeight = calculateWeight(selections);
        if (totalWeight <= 0)
            return Optional.empty();

        if (selections.size() == 1)
            return Optional.ofNullable(selections.getFirst().data());

        return makeSelectionInternal(selections, totalWeight, randomizer);
    }

    static <T> Optional<T> makeSelection(final List<? extends Entry<T>> selections, int totalWeight, IRandomizer randomizer) {
        if (totalWeight <= 0)
            return Optional.empty();

        return makeSelectionInternal(selections, totalWeight, randomizer);
    }

    static <T> int calculateWeight(final List<? extends Entry<T>> selections) {
        int totalWeight = 0;
        for (var selection : selections) {
            totalWeight += selection.weight.asInt();
        }
        return totalWeight;
    }

    private static <T> Optional<T> makeSelectionInternal(final List<? extends Entry<T>> selections, int totalWeight, IRandomizer randomizer) {
        int target = randomizer.nextInt(totalWeight);
        for (var selection : selections) {
            int weight = selection.weight.asInt();
            if (target < weight)
                return Optional.ofNullable(selection.data());
            target -= weight;
        }

        return Optional.empty();
    }

    public static class Entry<T> {

        protected final WeightValue weight;
        protected final T data;

        protected Entry(T data, WeightValue weight) {
            this.weight = weight;
            this.data = data;
        }

        public WeightValue weight() {
            return this.weight;
        }

        public T data() {
            return this.data;
        }
    }
}
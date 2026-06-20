package org.orecruncher.dsurround.lib;

import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Classic WeightTable for random weighted selection.
 */
public class WeightTable {

    public static <T> Optional<T> makeSelection(final Stream<? extends IItem<T>> inputStream) {
        return makeSelection(inputStream, Randomizer.current());
    }

    public static <T> Optional<T> makeSelection(final Stream<? extends IItem<T>> inputStream, IRandomizer randomizer) {
        return makeSelection(inputStream.toList(), randomizer);
    }

    public static <T> Optional<T> makeSelection(final List<? extends IItem<T>> selections, IRandomizer randomizer) {
        if (selections.isEmpty())
            return Optional.empty();

        if (selections.size() == 1)
            return Optional.ofNullable(selections.getFirst().data());

        int totalWeight = 0;
        for (var selection : selections) {
            totalWeight += Math.max(0, selection.getWeight().asInt());
        }

        if (totalWeight <= 0)
            return Optional.empty();

        int target = randomizer.nextInt(totalWeight);
        for (var selection : selections) {
            int weight = Math.max(0, selection.getWeight().asInt());
            if (target < weight)
                return Optional.ofNullable(selection.data());
            target -= weight;
        }

        return Optional.empty();
    }

    public interface IItem<T> {
        WeightValue getWeight();
        T data();
    }
}

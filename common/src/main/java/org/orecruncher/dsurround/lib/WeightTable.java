package org.orecruncher.dsurround.lib;

import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandom;
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
            return Optional.of(selections.getFirst().data());

        int totalWeight = WeightedRandom.getTotalWeight(selections);

        if (totalWeight == 0)
            return Optional.empty();

        int targetWeight = randomizer.nextInt(totalWeight);
        return WeightedRandom.getWeightedItem(selections, targetWeight).map(IItem::data);
    }

    public interface IItem<T> extends WeightedEntry {
        T data();
    }
}
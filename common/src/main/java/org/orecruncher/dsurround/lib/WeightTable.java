package org.orecruncher.dsurround.lib;

import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Classic WeightTable for random weighted selection.
 */
@SuppressWarnings("unused")
public class WeightTable {

    public static <T> Optional<T> makeSelection(final Stream<? extends IItem<T>> inputStream) {
        return makeSelection(inputStream.toList(), Randomizer.current());
    }

    public static <T> Optional<T> makeSelection(final Stream<? extends IItem<T>> inputStream, IRandomizer randomizer) {
        return makeSelection(inputStream.toList(), randomizer);
    }

    public static <T> Optional<T> makeSelection(final Collection<? extends IItem<T>> selections) {
        return makeSelection(selections, Randomizer.current());
    }

    public static <T> Optional<T> makeSelection(final Collection<? extends IItem<T>> selections, IRandomizer randomizer) {
        if (selections.isEmpty())
            return Optional.empty();

        if (selections.size() == 1) {
            T theItem;
            if (selections instanceof List<? extends IItem<T>> theList)
                theItem = theList.getFirst().getItem();
            else if (selections instanceof ObjectArray<? extends IItem<T>> theArray)
                theItem = theArray.getFirst().getItem();
            else
                theItem = selections.iterator().next().getItem();
            return Optional.of(theItem);
        }

        int totalWeight = 0;
        for (var e : selections)
            totalWeight += e.getWeight();
        if (totalWeight == 0)
            return Optional.empty();

        int targetWeight = randomizer.nextInt(totalWeight);

        for (var e : selections) {
            targetWeight -= e.getWeight();
            if (targetWeight < 0)
                return Optional.of(e.getItem());
        }

        // Shouldn't get here
        throw new RuntimeException("Bad weight table - ran off the end");
    }

    public interface IItem<T> {

        int getWeight();

        T getItem();
    }
}
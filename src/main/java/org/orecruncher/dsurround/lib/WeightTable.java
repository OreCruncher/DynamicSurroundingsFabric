package org.orecruncher.dsurround.lib;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Classic WeightTable for random weighted selection.
 *
 * @param <T>
 */
public class WeightTable<T> {

    private static final int DEFAULT_ARRAY_SIZE = 8;

    protected static final Random RANDOM = XorShiftRandom.current();

    protected int[] weightSegment = new int[DEFAULT_ARRAY_SIZE];
    protected Object[] item = new Object[DEFAULT_ARRAY_SIZE];
    protected int maxEntryIdx = 0;
    protected int totalWeight = 0;

    public WeightTable() {

    }

    public WeightTable(final Collection<? extends IItem<T>> input) {
        for (final IItem<T> i : input)
            add(i.getItem(), i.getWeight());
    }

    public void add(final T e, final int weight) {
        // Ignore bad entries
        if (weight <= 0)
            return;

        // Extend the array if needed
        if (this.maxEntryIdx == this.weightSegment.length) {
            this.weightSegment = Arrays.copyOf(this.weightSegment, this.weightSegment.length * 2);
            this.item = Arrays.copyOf(this.item, this.item.length * 2);
        }

        this.totalWeight += weight;
        this.weightSegment[this.maxEntryIdx] = this.totalWeight;
        this.item[this.maxEntryIdx] = e;
        this.maxEntryIdx++;
    }

    public void add(final IItem<T> entry) {
        this.add(entry.getItem(), entry.getWeight());
    }

    public int size() {
        return this.item.length;
    }

    @SuppressWarnings("unchecked")
    public @Nullable T next() {
        if (this.totalWeight <= 0)
            return null;

        if (this.item.length == 1)
            return (T) this.item[0];

        int targetWeight = RANDOM.nextInt(this.totalWeight);

        for (int i = 0; i < this.maxEntryIdx; i++)
            if (targetWeight < this.weightSegment[i])
                return (T) this.item[i];

        // Shouldn't get here
        throw new RuntimeException("Bad weight table - ran off the end");
    }

    public static <T> @Nullable T makeSelection(final List<? extends IItem<T>> input) {
        if (input.size() == 0)
            return null;
        if (input.size() == 1)
            return input.get(0).getItem();
        return new WeightTable<>(input).next();
    }

    public static <T> @Nullable T makeSelection(final Stream<? extends IItem<T>> inputStream) {
        WeightTable<T> table = null;
        var itr = inputStream.iterator();
        while (itr.hasNext()) {
            if (table == null)
                table = new WeightTable<>();
            table.add(itr.next());
        }
        return table != null ? table.next() : null;
    }

    public interface IItem<T> {

        int getWeight();

        T getItem();
    }

}
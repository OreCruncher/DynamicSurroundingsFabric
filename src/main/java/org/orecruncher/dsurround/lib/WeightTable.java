package org.orecruncher.dsurround.lib;

import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Classic WeightTable for random weighted selection.
 *
 * @param <T>
 */
public class WeightTable<T> {

    private static final int DEFAULT_ARRAY_SIZE = 8;

    protected static final IRandomizer RANDOM = Randomizer.current();

    protected int[] weightSegment = new int[DEFAULT_ARRAY_SIZE];
    protected Object[] item = new Object[DEFAULT_ARRAY_SIZE];
    protected int maxEntryIdx = 0;
    protected int totalWeight = 0;

    public WeightTable(Stream<? extends IItem<T>> inputStream) {
        this.add(inputStream);
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

    public void add(Stream<? extends IItem<T>> inputStream) {
        inputStream.forEach(this::add);
    }

    public void add(final IItem<T> entry) {
        this.add(entry.getItem(), entry.getWeight());
    }

    public int size() {
        return this.item.length;
    }

    public void clear() {
        this.totalWeight = 0;
        this.maxEntryIdx = 0;
    }

    @SuppressWarnings("unchecked")
    public Optional<T> next() {
        if (this.totalWeight <= 0)
            return Optional.empty();

        if (this.item.length == 1)
            return Optional.of((T) this.item[0]);

        int targetWeight = RANDOM.nextInt(this.totalWeight);

        for (int i = 0; i < this.maxEntryIdx; i++)
            if (targetWeight < this.weightSegment[i])
                return Optional.of((T) this.item[i]);

        // Shouldn't get here
        throw new RuntimeException("Bad weight table - ran off the end");
    }

    public static <T> Optional<T> makeSelection(final Stream<? extends IItem<T>> inputStream) {
        return new WeightTable<>(inputStream).next();
    }

    public interface IItem<T> {

        int getWeight();

        T getItem();
    }

}
package org.orecruncher.dsurround.lib;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;

import java.util.Collection;
import java.util.Random;

/**
 * Classic WeightTable for random weighted selection.
 *
 * @param <T>
 */
public class WeightTable<T> {

    protected static final Random RANDOM = XorShiftRandom.current();

    protected final ObjectArray<IItem<T>> entries = new ObjectArray<>();
    protected int totalWeight = 0;

    public WeightTable() {
    }

    public WeightTable(final IItem<T>[] items) {
        for (final IItem<T> i : items)
            add(i);
    }

    public WeightTable(final Collection<? extends IItem<T>> input) {
        for (final IItem<T> i : input)
            add(i);
    }

    public void add(final T e, final int weight) {
        add(new IItem<T>() {
            @Override
            public int getWeight() {
                return weight;
            }

            @Override
            public T getItem() {
                return e;
            }
        });
    }

    public void add(final IItem<T> entry) {
        entries.add(entry);
        totalWeight += entry.getWeight();
    }

    public int size() {
        return entries.size();
    }

    @Nullable
    public T next() {
        if (this.totalWeight <= 0)
            return null;

        if (this.entries.size() == 1)
            return this.entries.get(0).getItem();

        int targetWeight = RANDOM.nextInt(this.totalWeight);

        IItem<T> selected;
        int i = -1;
        do {
            selected = entries.get(++i);
            targetWeight -= selected.getWeight();
        } while (targetWeight >= 0);

        return selected.getItem();
    }

    public void trim() {
        this.entries.trim();
    }

    public interface IItem<T> {

        int getWeight();

        T getItem();
    }

}
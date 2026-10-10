package org.orecruncher.dsurround.lib.weighted;

import org.orecruncher.dsurround.lib.random.IRandomizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An immutable list of values with weights, for random weighted selection. Built with {@link #builder()}.
 */
public final class WeightedList<T> {

    private final List<WeightTable.Entry<T>> entries;
    private final int totalWeight;

    private WeightedList(List<WeightTable.Entry<T>> entries) {
        this.entries = entries;
        this.totalWeight = WeightTable.calculateWeight(this.entries);
    }

    /**
     * A value chosen at random, in proportion to the weights; empty if the list is empty or all weights are 0.
     */
    public Optional<T> getRandomValue(IRandomizer random) {
        return WeightTable.makeSelection(this.entries, this.totalWeight, random);
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private final List<WeightTable.Entry<T>> entries = new ArrayList<>(4);

        private Builder() {
        }

        public Builder<T> add(T value, int weight) {
            return this.add(value, WeightValue.of(weight));
        }

        public Builder<T> add(T value, WeightValue weight) {
            this.entries.add(new WeightTable.Entry<>(value, weight));
            return this;
        }

        /**
         * The list. It has its own copy of the entries, so adding more to the builder afterwards doesn't change it.
         */
        public WeightedList<T> build() {
            return new WeightedList<>(List.copyOf(this.entries));
        }
    }
}

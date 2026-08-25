package org.orecruncher.dsurround.lib.weighted;

import org.orecruncher.dsurround.lib.random.IRandomizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Small weighted-list helper used in places where Minecraft 26.x removed SimpleWeightedRandomList.
 */
public final class WeightedList<T> {
    private static final WeightedList<?> EMPTY = new WeightedList<>(List.of());

    private final List<WeightTable.Entry<T>> entries;
    private final int totalWeight;

    private WeightedList(List<WeightTable.Entry<T>> entries) {
        this.entries = entries;
        this.totalWeight = WeightTable.calculateWeight(this.entries);
    }

    @SuppressWarnings("unchecked")
    public static <T> WeightedList<T> empty() {
        return (WeightedList<T>) EMPTY;
    }

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

       public WeightedList<T> build() {
           return new WeightedList<>(this.entries);
       }
   }
}
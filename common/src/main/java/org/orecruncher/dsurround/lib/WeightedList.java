package org.orecruncher.dsurround.lib;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Small weighted-list helper used in places where Minecraft 26.x removed SimpleWeightedRandomList.
 */
public final class WeightedList<T> {
    private static final WeightedList<?> EMPTY = new WeightedList<>(List.of());

    private final List<Entry<T>> entries;

    private WeightedList(List<Entry<T>> entries) {
        this.entries = List.copyOf(entries);
    }

    @SuppressWarnings("unchecked")
    public static <T> WeightedList<T> empty() {
        return (WeightedList<T>) EMPTY;
    }

    public Optional<T> getRandomValue(RandomSource random) {
        if (this.entries.isEmpty()) {
            return Optional.empty();
        }

        int total = this.entries.stream().mapToInt(Entry::weight).filter(v -> v > 0).sum();
        if (total <= 0) {
            return Optional.of(this.entries.getFirst().value());
        }

        int roll = random.nextInt(total);
        for (Entry<T> entry : this.entries) {
            int weight = Math.max(0, entry.weight());
            if (roll < weight) {
                return Optional.of(entry.value());
            }
            roll -= weight;
        }

        return Optional.of(this.entries.getLast().value());
    }

    public static final class Builder<T> {
        private final List<Entry<T>> entries = new ArrayList<>();

        public Builder<T> add(T value, int weight) {
            this.entries.add(new Entry<>(value, weight));
            return this;
        }

        public WeightedList<T> build() {
            return new WeightedList<>(this.entries);
        }
    }

    private record Entry<T>(T value, int weight) { }
}

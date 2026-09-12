package org.orecruncher.dsurround.lib.collections;

import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple map implementation backed by a list. Intended to be used when there are a very small number of entries
 * expected to be stored.
 */
public class ListMap<K, V> implements Map<K, V> {

    private static final int NOT_FOUND = -1;
    private static final int DEFAULT_SIZE = 2;

    private final ObjectArray<Map.Entry<K, V>> entries;

    public ListMap() {
        this(DEFAULT_SIZE);
    }

    public ListMap(int initialCapacity) {
        this.entries = new ObjectArray<>(initialCapacity);
    }

    @Override
    public int size() {
        return this.entries.size();
    }

    @Override
    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return this.indexOf(key) != NOT_FOUND;
    }

    @Override
    public boolean containsValue(Object value) {
        if (this.isEmpty()) {
            return false;
        }

        for (Map.Entry<K, V> entry : this.entries) {
            if (entry.getValue().equals(value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V get(Object key) {
        var entry = this.entryOf(key);
        if (entry != null)
            return entry.getValue();
        return null;
    }

    @Override
    public @Nullable V put(K key, V value) {
        var entry = this.entryOf(key);
        if (entry == null) {
            this.entries.add(Map.entry(key, value));
            return null;
        }
        var lastValue = entry.getValue();
        entry.setValue(value);
        return lastValue;
    }

    @Override
    public V remove(Object key) {
        var index = this.indexOf(key);
        if (index == NOT_FOUND) {
            return null;
        }
        var entry = this.entries.get(index);
        this.entries.remove0(index);
        return entry.getValue();
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> m) {
        for (var entry : m.entrySet()) {
            this.entries.add(Map.entry(entry.getKey(), entry.getValue()));
        }
    }

    @Override
    public void clear() {
        this.entries.clear();
    }

    @Override
    public @NotNull Set<K> keySet() {
        if (this.isEmpty()) {
            return Set.of();
        }
        return this.entries.stream().map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    @Override
    public @NotNull Collection<V> values() {
        if (this.isEmpty()) {
            return List.of();
        }
        return this.entries.stream().map(Map.Entry::getValue).collect(Collectors.toList());
    }

    @Override
    public @NotNull Set<Entry<K, V>> entrySet() {
        throw new NotImplementedException();
    }

    private Map.Entry<K,V> entryOf(Object key) {
        if (this.isEmpty()) {
            return null;
        }

        for (Map.Entry<K, V> entry : this.entries) {
            if (entry.getKey().equals(key)) {
                return entry;
            }
        }

        return null;
    }

    private int indexOf(Object key) {
        if (this.isEmpty()) {
            return NOT_FOUND;
        }

        for (int i = 0; i < this.entries.size(); i++) {
            if (key.equals(this.entries.get(i).getKey())) {
                return i;
            }
        }
        return NOT_FOUND;
    }
}

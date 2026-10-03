package org.orecruncher.dsurround.config.libraries.impl;

import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Holds one value worked out from a key (compared by identity, e.g. the current level) and valid for one version of
 * a library. It is recomputed when either changes, so it never needs events to tell it the value is out of date.
 * Not thread-safe.
 */
final class VersionedCache<K, V> {

    private @Nullable K key;
    private int version;
    private @Nullable V value;

    /**
     * The value for {@code key} at {@code version}: the cached one if both match, otherwise a new one from
     * {@code compute}.
     */
    V get(K key, int version, Function<K, V> compute) {
        if (this.value == null || this.key != key || this.version != version) {
            this.value = compute.apply(key);
            this.key = key;
            this.version = version;
        }
        return this.value;
    }

    /**
     * Forgets the value, and the key, so the key can be collected.
     */
    void clear() {
        this.key = null;
        this.value = null;
    }
}

package org.orecruncher.dsurround.lib;

import java.util.function.Supplier;

/**
 * Similar to a Singleton, but able to refresh the value when needed.
 */
public class Lazy<T> extends Singleton<T> {

    public Lazy(final Supplier<T> supplier) {
        super(supplier);
    }

    @SuppressWarnings("unchecked")
    public void reset() {
        this.value = (T) NO_INIT;
    }

    @Override
    public String toString() {
        return String.format("Lazy [%s]", value != null ? value.toString() : "null");
    }
}
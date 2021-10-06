package org.orecruncher.dsurround.lib;

import java.util.function.Supplier;

public final class Lazy<T> {

    private final Supplier<T> supplier;

    private T value;

    public Lazy(final Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public void reset() {
        this.value = null;
    }

    public T get() {
        if (this.value == null)
            this.value = this.supplier.get();
        return this.value;
    }

    @Override
    public String toString() {
        return String.format("Lazy [%s]", value != null ? value.toString() : "null");
    }
}
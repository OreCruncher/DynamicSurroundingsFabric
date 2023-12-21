package org.orecruncher.dsurround.lib;

import com.google.common.base.Preconditions;

import java.util.function.Supplier;

public class Lazy<T> implements Supplier<T> {

    private static final Object UNINITIALIZED = new Object();

    private final Supplier<T> supplier;

    private volatile T value;

    @SuppressWarnings("unchecked")
    public Lazy(final Supplier<T> supplier) {
        Preconditions.checkNotNull(supplier);
        this.value = (T) UNINITIALIZED;
        this.supplier = supplier;
    }

    @Override
    public T get() {
        if (this.value == UNINITIALIZED)
            synchronized (this) {
                if (this.value == UNINITIALIZED)
                    this.value = this.supplier.get();
            }
        return this.value;
    }

    @SuppressWarnings("unchecked")
    public void reset() {
        this.value = (T) UNINITIALIZED;
    }

    @Override
    public String toString() {
        return String.format("Lazy [%s]", value != null ? value.toString() : "null");
    }
}
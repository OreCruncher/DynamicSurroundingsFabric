package org.orecruncher.dsurround.lib;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * A supplier that will lazily initialize a permanent value.
 */
public final class SingletonSupplier<T> implements Supplier<T> {

    private final CachingSupplier<T> supplier;

    private SingletonSupplier(final Supplier<T> delegate) {
        Preconditions.checkNotNull(delegate);
        this.supplier = CachingSupplier.from(delegate);
    }

    /**
     * Initializes the instance with a predetermined value
     */
    private SingletonSupplier(final T instance) {
        this.supplier = new CachingSupplier<>(instance);
    }

    @Nullable
    public T get() {
        return this.supplier.get();
    }

    public static <T> SingletonSupplier<T> of(T instance) {
        return new SingletonSupplier<>(instance);
    }

    public static <T> SingletonSupplier<T> from(Supplier<T> factory) {
        return new SingletonSupplier<>(factory);
    }
}
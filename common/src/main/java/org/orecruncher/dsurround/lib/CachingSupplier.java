package org.orecruncher.dsurround.lib;

import com.google.common.base.Supplier;

/**
 * A supplier that caches and reuses a result. Can be reset in order to generate a new value.
 */
public final class CachingSupplier<T> implements Supplier<T> {

    // Sentinel used to indicate that the value has not been initialized. Possible
    // that the wrapped delegate can return null as a valid response.
    private static final Object NO_INIT = new Object();

    private final Supplier<T> delegate;
    private volatile T value;

    @SuppressWarnings("unchecked")
    private CachingSupplier(final Supplier<T> delegate) {
        this.delegate = delegate;
        this.value = (T) NO_INIT;
    }

    // Package private so SingletonSupplier can access
    CachingSupplier(final T instance) {
        this.delegate = () -> { throw new RuntimeException("Should never get here"); };
        this.value = instance;
    }

    @Override
    public T get() {
        T result = this.value;

        if (result == NO_INIT)
            synchronized (this) {
                result = this.value;
                if (result == NO_INIT)
                    this.value = result = this.delegate.get();
            }

        return result;
    }

    @SuppressWarnings("unchecked")
    public void clear() {
        this.value = (T) NO_INIT;
    }

    public static <T> CachingSupplier<T> from(final Supplier<T> supplier) {
        return new CachingSupplier<>(supplier);
    }
}
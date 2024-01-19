package org.orecruncher.dsurround.lib;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class Singleton<T> implements Supplier<T> {

    protected static final Object NO_INIT = new Object();

    private final Supplier<T> factory;
    @SuppressWarnings("unchecked")
    protected volatile T value = (T) NO_INIT;

    public Singleton(final Supplier<T> factory) {
        this.factory = factory;
    }

    /**
     * Initializes the instance with a predetermined value
     */
    public Singleton(T instance) {
        this.factory = () -> { throw new RuntimeException("Should never get here"); };
        this.value = instance;
    }

    @Nullable
    public T get() {
        T result = this.value;

        if (result == NO_INIT)
            synchronized (this) {
                result = this.value;
                if (result == NO_INIT)
                    this.value = result = this.factory.get();
            }
        return result;
    }
}
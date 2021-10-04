package org.orecruncher.dsurround.lib;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class Singleton<T> {

    private static final Object NO_INIT = new Object();

    private final Supplier<T> factory;
    @SuppressWarnings("unchecked")
    private volatile T instance = (T) NO_INIT;

    public Singleton(final Supplier<T> factory) {
        this.factory = factory;
    }

    @Nullable
    public T get() {
        T result = this.instance;

        if (result == NO_INIT)
            synchronized (this) {
                result = this.instance;
                if (result == NO_INIT)
                    this.instance = result = this.factory.get();
            }
        return result;
    }
}
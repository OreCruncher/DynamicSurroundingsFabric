package org.orecruncher.dsurround.lib.events;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * An event whose handlers are called in registration order.
 * <p>
 * Thread-safe: registering builds a new invoker, so {@link #invoker()} is a plain read.
 */
final class Event<IHandler> implements IEvent<IHandler> {

    private final List<IHandler> eventHandlers = new ArrayList<>(4);
    private final Function<List<IHandler>, IHandler> invokerFactory;
    private volatile IHandler invoker;

    Event(Function<List<IHandler>, IHandler> invokerFactory) {
        this.invokerFactory = Preconditions.checkNotNull(invokerFactory);
        this.invoker = invokerFactory.apply(List.of());
    }

    @Override
    public synchronized void register(IHandler handler) {
        Preconditions.checkNotNull(handler);
        this.eventHandlers.add(handler);
        this.invoker = this.invokerFactory.apply(ImmutableList.copyOf(this.eventHandlers));
    }

    @Override
    public IHandler invoker() {
        return this.invoker;
    }
}

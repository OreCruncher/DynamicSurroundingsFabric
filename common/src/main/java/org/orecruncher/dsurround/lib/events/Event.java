package org.orecruncher.dsurround.lib.events;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class Event<IHandler> implements IEvent<IHandler> {

    private final List<IHandler> eventHandlers;
    private final Function<List<IHandler>, IHandler> eventLoopFactory;
    private IHandler eventLoop;

    Event(Function<List<IHandler>, IHandler> eventLoopFactory) {
        Preconditions.checkNotNull(eventLoopFactory);

        this.eventLoopFactory = eventLoopFactory;
        this.eventHandlers = new ArrayList<>(4);
        this.eventLoop = null;
    }

    @Override
    public void register(IHandler handler) {
        Preconditions.checkNotNull(handler);
        this.eventHandlers.add(handler);
        this.eventLoop = null;
    }

    @Override
    public IHandler invoker() {
        if (this.eventLoop == null) {
            var handlers = ImmutableList.copyOf(this.eventHandlers);
            this.eventLoop = this.eventLoopFactory.apply(handlers);
        }
        return this.eventLoop;
    }

    public static <IHandler> Event<IHandler> of(Function<List<IHandler>, IHandler> callbackFactory) {
        return new Event<>(callbackFactory);
    }
}

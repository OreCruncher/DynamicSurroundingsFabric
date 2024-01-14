package org.orecruncher.dsurround.lib.events.internal;

import com.google.common.base.Preconditions;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.events.IEvent;

import java.util.Collection;
import java.util.function.Function;

final class Event<THandler> implements IEvent<THandler> {

    private final Collection<THandler> _handlers;
    private final THandler _callbackProcessor;

    public Event(Function<Collection<THandler>, THandler> callbackFactory) {
        Preconditions.checkNotNull(callbackFactory);

        this._handlers = new ObjectArray<>(4);
        this._callbackProcessor = callbackFactory.apply(this._handlers);
    }

    @Override
    public void register(THandler handler) {
        Preconditions.checkNotNull(handler);

        this._handlers.add(handler);
    }

    @Override
    public THandler raise() {
        return this._callbackProcessor;
    }
}

package org.orecruncher.dsurround.lib.events.internal;

import org.orecruncher.dsurround.lib.events.IEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class Event<TEntityType> implements IEvent<TEntityType> {

    private final List<Consumer<TEntityType>> _handlers;
    private final BiConsumer<TEntityType, List<Consumer<TEntityType>>> _callbackProcessor;

    public Event(BiConsumer<TEntityType, List<Consumer<TEntityType>>> callbackProcessor) {
        this._handlers = new ArrayList<>(4);
        this._callbackProcessor = callbackProcessor;
    }

    @Override
    public void register(Consumer<TEntityType> handler) {
        this._handlers.add(handler);
    }

    @Override
    public void raise(TEntityType entity) {
        if (!this._handlers.isEmpty())
            this._callbackProcessor.accept(entity, Collections.unmodifiableList(this._handlers));
    }

    @Override
    public void clear() {
        this._handlers.clear();
    }
}

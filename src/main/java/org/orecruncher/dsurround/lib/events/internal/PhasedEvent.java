package org.orecruncher.dsurround.lib.events.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import org.orecruncher.dsurround.lib.events.EventPhase;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

import java.lang.reflect.Array;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Simple implementation of IEvent for callbacks processing.
 *
 * @param <TEntityType> The type of information passed into callback handlers
 */
final class PhasedEvent<TEntityType> implements IPhasedEvent<TEntityType> {

    private final ImmutableList<EventPhase> _phasedOrdering;

    // Basically a phased event is an aggregate of simple events based on
    // the list of phases.
    private final Event<TEntityType>[] _eventHandlers;

    @SuppressWarnings("unchecked")
    public PhasedEvent(ImmutableList<EventPhase> phasedOrdering, BiConsumer<TEntityType, List<Consumer<TEntityType>>> callbackProcessor) {
        this._phasedOrdering = phasedOrdering;

        Class<?> entityType = new TypeToken<Event<TEntityType>>() {
        }.getRawType();
        this._eventHandlers = (Event<TEntityType>[]) Array.newInstance(entityType, this._phasedOrdering.size());

        for (int i = 0; i < this._eventHandlers.length; i++)
            this._eventHandlers[i] = new Event<>(callbackProcessor);
    }

    @Override
    public void register(Consumer<TEntityType> handler) {
        // Register the handler with the default phase
        this.register(handler, EventPhase.DEFAULT);
    }

    @Override
    public void register(Consumer<TEntityType> handler, EventPhase phase) {
        // Identifier could be the same semantically as an entry in the phase order list.
        // We want to use the reference that is in the event definition to act as
        // the identity key.  Besides, this will also ensure that the requested
        // identifier is actually a phase for this event.
        this.getHandler(phase).register(handler);
    }

    @Override
    public void raise(TEntityType entity) {
        // Process in phased order
        for (var eventHandler : this._eventHandlers)
            eventHandler.raise(entity);
    }

    @Override
    public void raise(TEntityType entity, EventPhase phase) {
        this.getHandler(phase).raise(entity);
    }

    private Event<TEntityType> getHandler(EventPhase phase) {
        for (int i = 0; i < this._phasedOrdering.size(); i++)
            if (this._phasedOrdering.get(i).equals(phase))
                return this._eventHandlers[i];
        throw new IllegalArgumentException(String.format("The event does not understand phase '%s'", phase.toString()));
    }

    @Override
    public void clear() {
        for (var eventHandler : this._eventHandlers)
            eventHandler.clear();
    }
}

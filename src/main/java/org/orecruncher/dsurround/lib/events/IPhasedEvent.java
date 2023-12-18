package org.orecruncher.dsurround.lib.events;

import java.util.function.Consumer;

public interface IPhasedEvent<TEntityType> extends IEvent<TEntityType> {

    /**
     * Registers a callback handler for the specified phase of the event.
     *
     * @param handler Handler to register with the event implementation
     * @param phase   Phase of event processing to register the handler with
     */
    void register(Consumer<TEntityType> handler, EventPhase phase);

    /**
     * Raises an event with the specified phase
     *
     * @param entity Entity to pass into handlers
     * @param phase  Phase of the event that needs to be raised
     */
    void raise(TEntityType entity, EventPhase phase);
}

package org.orecruncher.dsurround.lib.events;

import java.util.function.Consumer;

public interface IEvent<TEntityType> {

    /**
     * Registers an event handler with the event
     *
     * @param handler Callback handler to register
     */
    void register(Consumer<TEntityType> handler);

    /**
     * Raises an event passing in the entity to each callback handler.
     *
     * @param entity Entity to pass along to callback handlers
     */
    void raise(TEntityType entity);

    /**
     * Clears the callback handler list of the event.
     */
    void clear();
}

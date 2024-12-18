package org.orecruncher.dsurround.lib.events;

public interface IEvent<THandler> {

    /**
     * Registers an event handler with the event
     *
     * @param handler Callback handler to register
     */
    void register(THandler handler);

    /**
     * Raises an event passing in the entity to each callback handler.
     *
     * @return Handler to perform the necessary processing
     */
    THandler raise();
}

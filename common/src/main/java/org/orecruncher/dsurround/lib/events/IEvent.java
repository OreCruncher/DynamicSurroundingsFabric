package org.orecruncher.dsurround.lib.events;

public interface IEvent<THandler> {

    /**
     * Registers an event handler with the event
     *
     * @param handler Callback handler to register
     */
    void register(THandler handler);

    /**
     * Obtains an invoker to be used for raising the event
     *
     * @return Invoker to perform the necessary processing
     */
    THandler invoker();
}

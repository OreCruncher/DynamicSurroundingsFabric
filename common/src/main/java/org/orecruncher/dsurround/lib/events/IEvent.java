package org.orecruncher.dsurround.lib.events;

/**
 * An event: handlers are registered, and the invoker raises the event by calling them all.
 * <p>
 * A handler that throws doesn't stop the others; the exception is logged (see
 * {@link EventingFactory#handlerFailed}).
 */
public interface IEvent<THandler> {

    /**
     * Registers an event handler with the event
     *
     * @param handler Callback handler to register
     */
    void register(THandler handler);

    /**
     * Obtains an invoker to be used for raising the event. It calls the handlers registered when it was obtained;
     * a handler registered later (including by a handler while the event is being raised) is called from the next
     * {@code invoker()} on.
     *
     * @return Invoker to perform the necessary processing
     */
    THandler invoker();
}

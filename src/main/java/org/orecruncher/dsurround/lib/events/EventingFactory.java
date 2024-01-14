package org.orecruncher.dsurround.lib.events;

import org.orecruncher.dsurround.lib.events.internal.EventFactoryImpl;

import java.util.Collection;
import java.util.function.Function;

public final class EventingFactory {
    private EventingFactory() {
    }

    /**
     * Creates an event using custom callback processing with prioritization.
     *
     * @param callbackFactory   Factory for creating a callback handler
     * @param <THandler>     The type of entity that will be passed into callback handlers
     * @return Newly constructed event reference
     */
    public static <THandler> IPhasedEvent<THandler> createPrioritizedEvent(Function<Collection<THandler>, THandler> callbackFactory) {
        return EventFactoryImpl.createPhasedEvent(callbackFactory, HandlerPriority.PHASED_ORDERING);
    }

    /**
     * Creates an event with custom callback processing
     *
     * @param callbackFactory   Factory for creating a callback handler
     * @param <THandler>     The type of entity that will be passed into callback handlers
     * @return Newly constructed event reference
     */
    public static <THandler> IEvent<THandler> createEvent(Function<Collection<THandler>, THandler> callbackFactory) {
        return EventFactoryImpl.createEvent(callbackFactory);
    }

}

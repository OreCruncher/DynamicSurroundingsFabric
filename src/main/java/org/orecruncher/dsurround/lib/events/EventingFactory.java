package org.orecruncher.dsurround.lib.events;

import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.events.internal.EventFactoryImpl;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class EventingFactory {
    private EventingFactory() {
    }

    /**
     * Creates an event using standard callback processing with prioritization.
     *
     * @param <TEntityType> The type of entity that will be passed into callback handlers
     * @return Newly constructed event reference
     */
    public static <TEntityType> IPhasedEvent<TEntityType> createPrioritizedEvent() {
        return createPrioritizedEvent(EventingFactory::defaultCallbackProcessor);
    }

    /**
     * Creates an event using custom callback processing with prioritization.
     *
     * @param callbackProcessor Callback handler to invoke when processing
     * @param <TEntityType>     The type of entity that will be passed into callback handlers
     * @return Newly constructed event reference
     */
    public static <TEntityType> IPhasedEvent<TEntityType> createPrioritizedEvent(BiConsumer<TEntityType, List<Consumer<TEntityType>>> callbackProcessor) {
        return EventFactoryImpl.createPhasedEvent(callbackProcessor, HandlerPriority.PHASED_ORDERING);
    }

    /**
     * Creates an event with standard callback processing
     *
     * @param <TEntityType> The type of entity that will be passed into callback handlers
     * @return Newly constructed event reference
     */
    public static <TEntityType> IEvent<TEntityType> createEvent() {
        return createEvent(EventingFactory::defaultCallbackProcessor);
    }

    /**
     * Creates an event with custom callback processing
     *
     * @param callbackProcessor Callback handler to invoke when processing
     * @param <TEntityType>     The type of entity that will be passed into callback handlers
     * @return Newly constructed event reference
     */
    public static <TEntityType> IEvent<TEntityType> createEvent(BiConsumer<TEntityType, List<Consumer<TEntityType>>> callbackProcessor) {
        return EventFactoryImpl.createEvent(callbackProcessor);
    }

    /**
     * Default callback processor
     */
    private static <TEntityType> void defaultCallbackProcessor(TEntityType entity, List<Consumer<TEntityType>> handlers) {
        for (var handler : handlers)
            try {
                handler.accept(entity);
            } catch (Throwable t) {
                Library.getLogger().error("Exception processing handler", t);
                throw t;
            }
    }
}

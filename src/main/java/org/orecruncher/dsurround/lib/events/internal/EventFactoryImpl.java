package org.orecruncher.dsurround.lib.events.internal;

import org.orecruncher.dsurround.lib.events.EventPhases;
import org.orecruncher.dsurround.lib.events.IEvent;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class EventFactoryImpl {
    private EventFactoryImpl() {
    }

    public static <TEntityType> IPhasedEvent<TEntityType> createPhasedEvent(BiConsumer<TEntityType, List<Consumer<TEntityType>>> callbackProcessor, EventPhases phasedOrdering) {
        if (callbackProcessor == null)
            throw new IllegalArgumentException("Callback processor must be provided");
        // Ensure the default phase is in the list
        return new PhasedEvent<>(phasedOrdering.getPhases(), callbackProcessor);
    }

    public static <TEntityType> IEvent<TEntityType> createEvent(BiConsumer<TEntityType, List<Consumer<TEntityType>>> callbackProcessor) {
        if (callbackProcessor == null)
            throw new IllegalArgumentException("Callback handler must be provided");
        return new Event<>(callbackProcessor);
    }
}

package org.orecruncher.dsurround.lib.events.internal;

import com.google.common.base.Preconditions;
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
        Preconditions.checkNotNull(callbackProcessor, "Callback processor must be provided");
        Preconditions.checkNotNull(phasedOrdering, "Phased ordering must be provided");

        // Ensure the default phase is in the list
        return new PhasedEvent<>(phasedOrdering.getPhases(), callbackProcessor);
    }

    public static <TEntityType> IEvent<TEntityType> createEvent(BiConsumer<TEntityType, List<Consumer<TEntityType>>> callbackProcessor) {
        Preconditions.checkNotNull(callbackProcessor, "Callback handler must be provided");

        return new Event<>(callbackProcessor);
    }
}

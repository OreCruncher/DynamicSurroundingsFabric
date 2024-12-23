package org.orecruncher.dsurround.lib.events.internal;

import com.google.common.base.Preconditions;
import org.orecruncher.dsurround.lib.events.EventPhases;
import org.orecruncher.dsurround.lib.events.IEvent;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

import java.util.Collection;
import java.util.function.Function;

public final class EventFactoryImpl {
    private EventFactoryImpl() {
    }

    public static <THandler> IPhasedEvent<THandler> createPhasedEvent(Function<Collection<THandler>, THandler> callbackProcessor, EventPhases phasedOrdering) {
        Preconditions.checkNotNull(callbackProcessor, "Callback processor must be provided");
        Preconditions.checkNotNull(phasedOrdering, "Phased ordering must be provided");

        // Ensure the default phase is in the list
        return new PhasedEvent<>(phasedOrdering.getPhases(), callbackProcessor);
    }

    public static <THandler> IEvent<THandler> createEvent(Function<Collection<THandler>, THandler> callbackProcessor) {
        Preconditions.checkNotNull(callbackProcessor, "Callback handler must be provided");

        return new Event<>(callbackProcessor);
    }
}

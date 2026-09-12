package org.orecruncher.dsurround.lib.events;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.*;
import java.util.function.Function;

/**
 * Simple implementation of IEvent for callback processing.
 *
 * @param <IHandler> The type of information passed into callback handlers
 */
final class PhasedEvent<IHandler> implements IPhasedEvent<IHandler> {

    private final ImmutableList<EventPhase> phasedOrdering;
    private final List<Map.Entry<Integer, IHandler>> eventHandlers = new ArrayList<>(10);
    private final Function<List<IHandler>, IHandler> eventLoopFactory;
    private IHandler eventLoop;

    PhasedEvent(ImmutableList<EventPhase> phasedOrdering, Function<List<IHandler>, IHandler> eventLoopFactory) {
        Preconditions.checkNotNull(phasedOrdering);
        Preconditions.checkArgument(!phasedOrdering.isEmpty(), "At least one entry needs to be provided");
        Preconditions.checkNotNull(eventLoopFactory);

        this.phasedOrdering = phasedOrdering;
        this.eventLoopFactory = eventLoopFactory;
    }

    @Override
    public void register(IHandler handler) {
        Preconditions.checkNotNull(handler);

        // Register the handler with the default phase
        this.register(handler, EventPhase.DEFAULT);
    }

    @Override
    public IHandler invoker() {
        if (this.eventLoop == null) {
            // Sort the handlers based on priorities, and then construct
            // a new event loop
            this.eventHandlers.sort(Map.Entry.comparingByKey());
            var handlerList = ImmutableList.copyOf(this.eventHandlers.stream().map(Map.Entry::getValue).iterator());
            this.eventLoop = this.eventLoopFactory.apply(handlerList);
        }
        return this.eventLoop;
    }

    @Override
    public void register(IHandler handler, EventPhase phase) {
        Preconditions.checkNotNull(handler);
        Preconditions.checkNotNull(phase);

        this.eventHandlers.add(Map.entry(this.getPriority(phase), handler));
        this.eventLoop = null;
    }

    private int getPriority(EventPhase phase) {
        var index = this.phasedOrdering.indexOf(phase);
        if (index == -1)
            throw new IllegalArgumentException(String.format("The event does not understand phase '%s'", phase.toString()));
        return index;
    }

    public static <IHandler> IPhasedEvent<IHandler> of(EventPhases phasedOrdering, Function<List<IHandler>, IHandler> function) {
        return new PhasedEvent<>(phasedOrdering.getPhases(), function);
    }
}

package org.orecruncher.dsurround.lib.events.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.events.EventPhase;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

import java.util.Collection;
import java.util.function.Function;

/**
 * Simple implementation of IEvent for callback processing.
 *
 * @param <IHandler> The type of information passed into callback handlers
 */
final class PhasedEvent<IHandler> implements IPhasedEvent<IHandler> {

    private final ImmutableList<EventPhase> phasedOrdering;
    private final ObjectArray<IHandler> handlerSequence;
    private final ObjectArray<ObjectArray<IHandler>> eventHandlers;
    private final IHandler callback;

    public PhasedEvent(ImmutableList<EventPhase> phasedOrdering, Function<Collection<IHandler>, IHandler> callbackFactory) {
        Preconditions.checkNotNull(phasedOrdering);
        Preconditions.checkArgument(!phasedOrdering.isEmpty(), "At least one entry needs to be provided");
        Preconditions.checkNotNull(callbackFactory);

        this.phasedOrdering = phasedOrdering;
        this.handlerSequence = new ObjectArray<>();
        this.eventHandlers = new ObjectArray<>(this.phasedOrdering.size());

        for(int i = 0; i < this.phasedOrdering.size(); i++)
            this.eventHandlers.add(new ObjectArray<>());

        this.callback = callbackFactory.apply(this.handlerSequence);

        this.updateHandlerSequence();
    }

    @Override
    public void register(IHandler handler) {
        Preconditions.checkNotNull(handler);

        // Register the handler with the default phase
        this.register(handler, EventPhase.DEFAULT);
    }

    @Override
    public IHandler raise() {
        return this.callback;
    }

    @Override
    public void register(IHandler handler, EventPhase phase) {
        Preconditions.checkNotNull(handler);
        Preconditions.checkNotNull(phase);

        this.getHandler(phase).add(handler);
        this.updateHandlerSequence();
    }

    private void updateHandlerSequence() {
        this.handlerSequence.clear();
        for (int i = 0; i < this.eventHandlers.size(); i++) {
            var handlerList = this.eventHandlers.get(i);
            for (int j = 0; j < handlerList.size(); j++)
                this.handlerSequence.add(handlerList.get(j));
        }
    }

    private Collection<IHandler> getHandler(EventPhase phase) {
        for (int i = 0; i < this.phasedOrdering.size(); i++)
            if (this.phasedOrdering.get(i).equals(phase))
                return this.eventHandlers.get(i);
        throw new IllegalArgumentException(String.format("The event does not understand phase '%s'", phase.toString()));
    }
}

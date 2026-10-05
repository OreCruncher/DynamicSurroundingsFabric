package org.orecruncher.dsurround.lib.events;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * An event whose handlers are registered with a phase and called in phase order; handlers in the same phase are
 * called in registration order.
 * <p>
 * Thread-safe: registering builds a new invoker, so {@link #invoker()} is a plain read.
 *
 * @param <IHandler> The handler interface
 */
final class PhasedEvent<IHandler> implements IPhasedEvent<IHandler> {

    private record Registration<H>(int phaseIndex, H handler) {
    }

    private final ImmutableList<EventPhase> phaseOrdering;
    // Kept sorted by phase index, registration order within a phase
    private final List<Registration<IHandler>> registrations = new ArrayList<>(10);
    private final Function<List<IHandler>, IHandler> invokerFactory;
    private volatile IHandler invoker;

    PhasedEvent(ImmutableList<EventPhase> phaseOrdering, Function<List<IHandler>, IHandler> invokerFactory) {
        Preconditions.checkNotNull(phaseOrdering);
        Preconditions.checkArgument(!phaseOrdering.isEmpty(), "At least one entry needs to be provided");
        this.phaseOrdering = phaseOrdering;
        this.invokerFactory = Preconditions.checkNotNull(invokerFactory);
        this.invoker = invokerFactory.apply(List.of());
    }

    @Override
    public void register(IHandler handler) {
        this.register(handler, EventPhase.DEFAULT);
    }

    @Override
    public synchronized void register(IHandler handler, EventPhase phase) {
        Preconditions.checkNotNull(handler);
        Preconditions.checkNotNull(phase);

        int phaseIndex = this.getPhaseIndex(phase);

        // Insert after every handler of the same or an earlier phase
        int insertAt = this.registrations.size();
        while (insertAt > 0 && this.registrations.get(insertAt - 1).phaseIndex() > phaseIndex)
            insertAt--;
        this.registrations.add(insertAt, new Registration<>(phaseIndex, handler));

        this.invoker = this.invokerFactory.apply(this.registrations.stream().map(Registration::handler).collect(ImmutableList.toImmutableList()));
    }

    @Override
    public IHandler invoker() {
        return this.invoker;
    }

    private int getPhaseIndex(EventPhase phase) {
        var index = this.phaseOrdering.indexOf(phase);
        if (index == -1)
            throw new IllegalArgumentException(String.format("The event does not understand phase '%s'", phase));
        return index;
    }
}

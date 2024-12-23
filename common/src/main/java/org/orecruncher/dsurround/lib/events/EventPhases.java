package org.orecruncher.dsurround.lib.events;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public final class EventPhases {

    private final ImmutableList<EventPhase> phases;

    EventPhases(EventPhase[] phases) {
        Preconditions.checkNotNull(phases);
        Preconditions.checkArgument(phases.length > 0, "At least one entry needs to be supplied");

        this.phases = ImmutableList.copyOf(phases);
    }

    public ImmutableList<EventPhase> getPhases() {
        return this.phases;
    }
}

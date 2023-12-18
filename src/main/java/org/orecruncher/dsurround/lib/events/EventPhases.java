package org.orecruncher.dsurround.lib.events;

import com.google.common.collect.ImmutableList;

public final class EventPhases {

    private final ImmutableList<EventPhase> phases;

    EventPhases(EventPhase[] phases) {
        this.phases = ImmutableList.copyOf(phases);
    }

    public ImmutableList<EventPhase> getPhases() {
        return this.phases;
    }
}

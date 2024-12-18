package org.orecruncher.dsurround.lib.events;

import com.google.common.base.Preconditions;

import java.util.HashSet;
import java.util.List;

public final class EventPhase {

    /**
     * Default phase if one is not specified.  All phased events need to include the default.
     */
    public static final EventPhase DEFAULT = EventPhase.of("event.phase.default");

    private final String _name;

    EventPhase(String phaseName) {
        Preconditions.checkNotNull(phaseName);

        this._name = phaseName;
    }

    public static EventPhase of(String... name) {
        Preconditions.checkNotNull(name);
        Preconditions.checkArgument(name.length > 0, "Name needs to be provided");

        return new EventPhase(String.join(".", name));
    }

    /**
     * Creates an ordered list of phases.  During event processing phases at the beginning of the order are processed
     * prior to phases at the end.  The list must include the default phase.
     *
     * @param phases Phases with relative ordering
     */
    public static EventPhases phaseOrderingOf(EventPhase... phases) {
        Preconditions.checkNotNull(phases);
        Preconditions.checkArgument(phases.length > 0, "Event phase specification is required");

        var set = new HashSet<>(List.of(phases));
        Preconditions.checkState(set.size() == phases.length, "Duplicate event phase specification detected");
        Preconditions.checkState(set.contains(DEFAULT), "Event phase specification does not include DEFAULT phase");

        return new EventPhases(phases);
    }

    public String getName() {
        return this._name;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        return object instanceof EventPhase phase && this._name.equals(phase._name);
    }

    @Override
    public int hashCode() {
        return this._name.hashCode();
    }

    @Override
    public String toString() {
        return String.format("EventPhase [%s]", this._name);
    }

}

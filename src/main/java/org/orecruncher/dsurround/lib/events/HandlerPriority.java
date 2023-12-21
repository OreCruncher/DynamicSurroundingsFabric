package org.orecruncher.dsurround.lib.events;

/**
 * Used to indicate relative priority of handling when event callbacks are being processed.  This technique
 * mirrors what fabric is doing with events.
 */
public final class HandlerPriority {
    public static final EventPhase VERY_HIGH = EventPhase.of("event.priority.very_high");
    public static final EventPhase HIGH = EventPhase.of("event.priority.high");
    public static final EventPhase NORMAL = EventPhase.DEFAULT;
    public static final EventPhase LOW = EventPhase.of("event.priority.low");
    public static final EventPhase VERY_LOW = EventPhase.of("event.priority.very_low");
    // Phase ordering of priority events
    public static final EventPhases PHASED_ORDERING = EventPhase.phaseOrderingOf(VERY_HIGH, HIGH, NORMAL, LOW, VERY_LOW);

    private HandlerPriority() {
    }
}

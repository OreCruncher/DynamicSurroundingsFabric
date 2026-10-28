package org.orecruncher.dsurround.profiling;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;

/**
 * A Java Flight Recorder event carrying how long one of the mod's client tick handlers took in one tick (see
 * {@link HandlerTicks}), written for every handler every tick while /dsprofile records, so a tick's handlers can be
 * added up and compared tick by tick. Only loaded while recording: the {@code jdk.jfr} module may not be there at all in
 * a player's Java.
 */
@Name("dsurround.HandlerTick")
@Label("Handler Tick")
@Category("Dynamic Surroundings")
@Description("How long a Dynamic Surroundings client tick handler took in one client tick")
@StackTrace(false)
public final class HandlerTickEvent extends Event {

    @Label("Tick")
    @Description("The client tick the handler ran in; the same for every handler of that tick")
    long tick;

    @Label("Handler")
    String handler;

    @Label("Time")
    @Timespan(Timespan.NANOSECONDS)
    long time;

    @Label("Ran")
    @Description("Whether the handler had work this tick; one that didn't only asked")
    boolean ran;
}

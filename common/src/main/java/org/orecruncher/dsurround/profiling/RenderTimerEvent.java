package org.orecruncher.dsurround.profiling;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * A Java Flight Recorder event carrying one of the mod's render timers (see
 * {@link org.orecruncher.dsurround.effects.RenderTimer}), written once a second while /dsprofile records, so the GPU's
 * time to draw each effect is in the recording beside the CPU's samples. Only loaded while recording: the {@code jdk.jfr}
 * module may not be there at all in a player's Java.
 */
@Name("dsurround.RenderTimer")
@Label("Render Timer")
@Category("Dynamic Surroundings")
@Description("How long a Dynamic Surroundings effect takes to draw, as the diagnostics overlay shows it")
public final class RenderTimerEvent extends Event {

    @Label("Effect")
    String effect;

    @Label("GPU average (ms)")
    double gpuAverage;

    @Label("GPU peak (ms)")
    double gpuPeak;

    @Label("CPU average (ms)")
    double cpuAverage;

    @Label("CPU peak (ms)")
    double cpuPeak;

    @Label("Count")
    @Description("How many of what the effect draws there were a frame, on average (Precipitation: drops in the area; "
            + "Drops: drops drawn; Damp Spots: spots); NaN for an effect that doesn't count")
    double count;
}

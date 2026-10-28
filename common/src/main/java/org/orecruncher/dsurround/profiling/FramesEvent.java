package org.orecruncher.dsurround.profiling;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * A Java Flight Recorder event carrying a second's frames (see {@link FrameTimes}), written once a second while
 * /dsprofile records, so each effect's render timer can be set against the frame it is part of. Only loaded while
 * recording: the {@code jdk.jfr} module may not be there at all in a player's Java.
 */
@Name("dsurround.Frames")
@Label("Frames")
@Category("Dynamic Surroundings")
@Description("How long the game's frames took in a second, and how long drawing one took on the GPU and the CPU")
public final class FramesEvent extends Event {

    @Label("Frames")
    int frames;

    @Label("Mean (ms)")
    @Description("The frames' mean length, from the start of one's drawing to the next's")
    double mean;

    @Label("Median (ms)")
    double p50;

    @Label("95th percentile (ms)")
    double p95;

    @Label("Longest (ms)")
    double max;

    @Label("GPU drawing (ms)")
    @Description("Drawing a frame on the GPU, the world and the screen over it; the running average, as the render timers'")
    double gpu;

    @Label("CPU drawing (ms)")
    @Description("Drawing a frame on the CPU, the world and the screen over it; the running average, as the render timers'")
    double cpu;

    static void write(FrameTimes.Second second) {
        var event = new FramesEvent();
        event.frames = second.frames();
        event.mean = second.mean();
        event.p50 = second.p50();
        event.p95 = second.p95();
        event.max = second.max();
        event.gpu = second.gpu();
        event.cpu = second.cpu();
        event.commit();
    }
}

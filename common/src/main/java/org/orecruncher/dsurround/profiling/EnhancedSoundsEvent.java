package org.orecruncher.dsurround.profiling;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * A Java Flight Recorder event carrying what enhanced sound processing did over the last second (see
 * {@link SoundSamples}), written once a second while /dsprofile records, whether or not anything played: a recording
 * of sounds that says processing wasn't running explains an empty one. Counts and times only: nothing of which sounds
 * or where. Only loaded while recording: the {@code jdk.jfr} module may not be there at all in a player's Java.
 */
@Name("dsurround.EnhancedSounds")
@Label("Enhanced Sounds")
@Category("Dynamic Surroundings")
@Description("What enhanced sound processing (reverb, occlusion) did over a second")
@StackTrace(false)
public final class EnhancedSoundsEvent extends Event {

    @Label("Available")
    @Description("Whether enhanced sound processing was running: off in the settings, or no sound device, and it isn't")
    boolean available;

    @Label("Sources")
    @Description("Sounds being processed, mean over the worker's passes")
    float sources;

    @Label("Sources Peak")
    @Description("The most sounds being processed in one pass")
    int sourcesPeak;

    @Label("Starts")
    @Description("Calculations made as sounds started, on the sound engine's thread, which waits for them")
    int starts;

    @Label("Start Mean")
    @Description("A start's calculation and upload, mean, in milliseconds")
    double startMean;

    @Label("Start Peak")
    @Description("The longest start, in milliseconds")
    double startPeak;

    @Label("Deferred")
    @Description("Sounds that started past the start budget, without a calculation, left to the worker threads")
    int deferred;

    @Label("Updates")
    @Description("Calculations handed to the worker threads, each sound about three times a second")
    int updates;

    @Label("Update Mean")
    @Description("An update's calculation, mean, in milliseconds")
    double updateMean;

    @Label("Update Peak")
    @Description("The longest update, in milliseconds")
    double updatePeak;

    @Label("Passes")
    @Description("The worker's passes, 20 a second: each hands out the updates due and waits for them")
    int passes;

    @Label("Pass Mean")
    @Description("A pass, mean, in milliseconds")
    double passMean;

    @Label("Pass Peak")
    @Description("The longest pass, in milliseconds")
    double passPeak;

    @Label("Uploads")
    @Description("Filter uploads to the sources, on the sound engine's thread")
    int uploads;

    @Label("Upload Mean")
    @Description("An upload, mean, in milliseconds")
    double uploadMean;

    @Label("Traced")
    @Description("Calculations that traced rays")
    int traced;

    @Label("Trace Mean")
    @Description("A trace (occlusion and reverb rays), mean, in milliseconds")
    double traceMean;

    @Label("Trace Peak")
    @Description("The longest trace, in milliseconds")
    double tracePeak;

    @Label("Shared")
    @Description("Calculations that took another sound's traced result (same blocks, nothing changed)")
    int shared;

    @Label("Reused")
    @Description("Calculations that reused the sound's own last result (it, the player and the world unchanged)")
    int reused;

    @Label("Skipped")
    @Description("Calculations skipped: the sound out of range, or nothing to calculate against")
    int skipped;

    static void write(boolean available, SoundSamples.Second second) {
        var event = new EnhancedSoundsEvent();
        event.available = available;
        var passes = second.passes();
        event.sources = passes.count() == 0 ? 0 : (float) second.sourcesSum() / passes.count();
        event.sourcesPeak = (int) second.sourcesPeak();
        event.starts = (int) second.starts().count();
        event.startMean = second.starts().meanMSecs();
        event.startPeak = second.starts().peakMSecs();
        event.deferred = (int) second.deferred();
        event.updates = (int) second.updates().count();
        event.updateMean = second.updates().meanMSecs();
        event.updatePeak = second.updates().peakMSecs();
        event.passes = (int) passes.count();
        event.passMean = passes.meanMSecs();
        event.passPeak = passes.peakMSecs();
        event.uploads = (int) second.uploads().count();
        event.uploadMean = second.uploads().meanMSecs();
        event.traced = (int) second.traces().count();
        event.traceMean = second.traces().meanMSecs();
        event.tracePeak = second.traces().peakMSecs();
        event.shared = (int) second.shared();
        event.reused = (int) second.reused();
        event.skipped = (int) second.skipped();
        event.commit();
    }
}

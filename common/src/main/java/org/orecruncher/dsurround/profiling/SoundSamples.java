package org.orecruncher.dsurround.profiling;

import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * What enhanced sound processing did, for the recording /dsprofile is making (see {@link EnhancedSoundsEvent}): the
 * calculations when sounds start (on the sound engine's thread, which waits for them), the worker's passes and the
 * calculations they hand out, the uploads to the sources, and what each calculation did: traced rays (timed), took
 * another sound's result, reused its own last one, or was skipped. Counted from several threads, so each count is an adder; while nothing records, nothing is counted
 * and nobody reads the clock. Uses nothing from {@code jdk.jfr}, so the sound code can call it in a Java without that
 * module.
 */
public final class SoundSamples {

    /**
     * One kind of timed work: how many, their time and the longest
     */
    static final class Timed {
        private final LongAdder count = new LongAdder();
        private final LongAdder nanos = new LongAdder();
        private final LongAccumulator peak = new LongAccumulator(Math::max, 0);

        void add(long elapsed) {
            this.count.increment();
            this.nanos.add(elapsed);
            this.peak.accumulate(elapsed);
        }

        Taken take() {
            return new Taken(this.count.sumThenReset(), this.nanos.sumThenReset(), this.peak.getThenReset());
        }
    }

    /**
     * A kind of work's count, total time and longest since the last take
     */
    record Taken(long count, long nanos, long peak) {
        double meanMSecs() {
            return this.count == 0 ? 0 : this.nanos / 1e6 / this.count;
        }

        double peakMSecs() {
            return this.peak / 1e6;
        }
    }

    /**
     * Everything counted since the last take
     *
     * @param starts      calculations made as sounds started
     * @param updates     calculations the worker handed out
     * @param passes      the worker's passes, each handing out the updates due and waiting for them
     * @param uploads     filter uploads to the sources, on the thread playing them
     * @param traces      calculations that traced rays, and the tracing's time
     * @param shared      calculations that took another sound's result
     * @param reused      calculations that reused the sound's own last result: nothing it depends on had changed
     * @param skipped     calculations skipped: the sound out of range, or nothing to calculate against
     * @param sourcesSum  the sounds being processed, added up over the passes
     * @param sourcesPeak the most sounds being processed in one pass
     */
    record Second(Taken starts, Taken updates, Taken passes, Taken uploads, Taken traces, long shared, long reused,
                  long skipped, long sourcesSum, long sourcesPeak) {
    }

    private static final Timed STARTS = new Timed();
    private static final Timed UPDATES = new Timed();
    private static final Timed PASSES = new Timed();
    private static final Timed UPLOADS = new Timed();
    private static final Timed TRACES = new Timed();
    private static final LongAdder SHARED = new LongAdder();
    private static final LongAdder REUSED = new LongAdder();
    private static final LongAdder SKIPPED = new LongAdder();
    private static final LongAdder SOURCES = new LongAdder();
    private static final LongAccumulator SOURCES_PEAK = new LongAccumulator(Math::max, 0);

    private static volatile boolean recording;
    // Whether enhanced sound processing is running, set as the sound engine starts and stops
    private static volatile boolean available;

    private SoundSamples() {
    }

    /**
     * Whether a recording wants the counts; asked before timing anything
     */
    public static boolean isRecording() {
        return recording;
    }

    /**
     * Whether enhanced sound processing is running: set as the sound engine starts and stops
     */
    public static void setAvailable(boolean running) {
        available = running;
    }

    static boolean isAvailable() {
        return available;
    }

    /**
     * A calculation made as a sound started, on the thread playing it
     */
    public static void started(long nanos) {
        STARTS.add(nanos);
    }

    /**
     * A calculation the worker handed out, on a worker thread
     */
    public static void updated(long nanos) {
        UPDATES.add(nanos);
    }

    /**
     * A worker pass, and the sounds being processed in it
     */
    public static void pass(long nanos, int sources) {
        PASSES.add(nanos);
        SOURCES.add(sources);
        SOURCES_PEAK.accumulate(sources);
    }

    /**
     * An upload of a source's filters, on the thread playing it
     */
    public static void uploaded(long nanos) {
        UPLOADS.add(nanos);
    }

    /**
     * A calculation that traced rays, and the tracing's time
     */
    public static void traced(long nanos) {
        TRACES.add(nanos);
    }

    /**
     * A calculation that took another sound's result
     */
    public static void shared() {
        SHARED.increment();
    }

    /**
     * A calculation that reused the sound's own last result
     */
    public static void reused() {
        REUSED.increment();
    }

    /**
     * A calculation skipped: the sound out of range, or nothing to calculate against
     */
    public static void skipped() {
        SKIPPED.increment();
    }

    static Second take() {
        return new Second(STARTS.take(), UPDATES.take(), PASSES.take(), UPLOADS.take(), TRACES.take(),
                SHARED.sumThenReset(), REUSED.sumThenReset(), SKIPPED.sumThenReset(), SOURCES.sumThenReset(),
                SOURCES_PEAK.getThenReset());
    }

    /**
     * Starts counting, from nothing
     */
    static void start() {
        take();
        recording = true;
    }

    static void stop() {
        recording = false;
    }
}

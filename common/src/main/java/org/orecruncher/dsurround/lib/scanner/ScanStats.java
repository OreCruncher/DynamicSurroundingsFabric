package org.orecruncher.dsurround.lib.scanner;

import org.orecruncher.dsurround.lib.math.EMA;
import org.orecruncher.dsurround.lib.math.TimerEMA;

/**
 * Per-tick scanner measurements for the diagnostics overlay: time spent, blocks read and sections skipped, as
 * moving averages over about a second, plus the slowest tick over the last few seconds.
 */
public final class ScanStats {

    private static final int AVERAGE_TICKS = 20;
    private static final int PEAK_WINDOW_TICKS = 100;

    private final TimerEMA time = new TimerEMA("scan", AVERAGE_TICKS);
    private final EMA blocksRead = new EMA("blocks", AVERAGE_TICKS);
    private final EMA sectionsSkipped = new EMA("skipped", AVERAGE_TICKS);

    private long peakNanos = 0;
    private long windowPeakNanos = 0;
    private int windowTicks = 0;

    private int rescans = 0;
    private int resets = 0;

    /**
     * Records one tick of scanning, including ticks with nothing to do.
     */
    void record(final long nanos, final int blocks, final int skipped) {
        this.time.update(nanos);
        this.blocksRead.update(blocks);
        this.sectionsSkipped.update(skipped);

        this.windowPeakNanos = Math.max(this.windowPeakNanos, nanos);
        if (++this.windowTicks >= PEAK_WINDOW_TICKS) {
            this.peakNanos = this.windowPeakNanos;
            this.windowPeakNanos = 0;
            this.windowTicks = 0;
        }
    }

    void countRescan() {
        this.rescans++;
    }

    void countReset() {
        this.resets++;
    }

    /**
     * Average milliseconds spent scanning per tick.
     */
    public double averageMillis() {
        return this.time.getMSecs();
    }

    /**
     * The slowest tick, in milliseconds, over the last few seconds.
     */
    public double peakMillis() {
        return Math.max(this.peakNanos, this.windowPeakNanos) / 1_000_000D;
    }

    /**
     * Average nanoseconds per block read, or NaN while nothing has been read.
     */
    public double nanosPerBlock() {
        double blocks = this.blocksRead.get();
        return blocks > 0 ? this.time.get() / blocks : Double.NaN;
    }

    public String summary() {
        return "time %.3fms avg, %.3fms peak | %.0f blocks/tick, %.0f sections skipped/tick, %.0f ns/block | rescans %d, resets %d"
                .formatted(this.averageMillis(), this.peakMillis(), this.blocksRead.get(), this.sectionsSkipped.get(),
                        this.nanosPerBlock(), this.rescans, this.resets);
    }
}

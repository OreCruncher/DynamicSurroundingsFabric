package org.orecruncher.dsurround.lib.math;

import java.util.function.LongSupplier;

/**
 * Specialization of the EMA based on measuring time. The time unit it expects
 * to deal with is nanoseconds.
 * <p>
 * Also keeps the peak: the largest sample of the last second or two, which the average hides (a hitch once a second
 * barely moves it). Samples are gathered over a second, and the peak is the largest of that second's and the one
 * before, so it doesn't drop to nothing as a second begins.
 */
public class TimerEMA extends EMA implements ITimer {

    static final long PEAK_WINDOW_NANOS = 1_000_000_000L;

    private final LongSupplier clock;
    private long windowStart = Long.MIN_VALUE;
    private double windowPeak;
    private double previousPeak;

    public TimerEMA(final String name) {
        super(name);
        this.clock = System::nanoTime;
    }

    public TimerEMA(final String name, final int periods) {
        this(name, periods, System::nanoTime);
    }

    /**
     * @param clock the time in nanoseconds, for the peak's windows
     */
    TimerEMA(final String name, final int periods, final LongSupplier clock) {
        super(name, periods);
        this.clock = clock;
    }

    @Override
    public double update(final double newValue) {
        this.roll();
        this.windowPeak = Math.max(this.windowPeak, newValue);
        return super.update(newValue);
    }

    public double getMSecs() {
        return super.get() / 1000000;
    }

    /**
     * The largest sample of the last second or two, in milliseconds; 0 if there were none
     */
    public double getPeakMSecs() {
        this.roll();
        return Math.max(this.windowPeak, this.previousPeak) / 1000000;
    }

    /**
     * Moves on to a new window once the current one is a second old
     */
    private void roll() {
        long now = this.clock.getAsLong();
        if (this.windowStart == Long.MIN_VALUE) {
            this.windowStart = now;
            return;
        }
        long elapsed = now - this.windowStart;
        if (elapsed < PEAK_WINDOW_NANOS)
            return;
        // A whole window or more went by without samples: nothing from before is recent
        this.previousPeak = elapsed < 2 * PEAK_WINDOW_NANOS ? this.windowPeak : 0D;
        this.windowPeak = 0D;
        this.windowStart = now;
    }

    @Override
    public String toString() {
        return String.format("%s:%7.3fms, peak%7.3f", name(), getMSecs(), getPeakMSecs());
    }
}

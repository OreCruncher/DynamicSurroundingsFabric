package org.orecruncher.dsurround.lib.function;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * A boolean that is costly to ask for and slow to change, asked for again at most once per interval; in between, the
 * last answer is given. For asking another mod by reflection, many times a frame, about a setting a player changes
 * now and then.
 */
public final class ThrottledBooleanSupplier implements BooleanSupplier {

    private final BooleanSupplier source;
    private final LongSupplier nanoClock;
    private final long intervalNanos;
    private volatile boolean value;
    private volatile long askedAt;
    private volatile boolean asked;

    /**
     * @param source        what to ask
     * @param intervalNanos how long an answer is kept, in nanoseconds
     * @param nanoClock     the time, in nanoseconds, such as System::nanoTime
     */
    public ThrottledBooleanSupplier(BooleanSupplier source, long intervalNanos, LongSupplier nanoClock) {
        this.source = source;
        this.intervalNanos = intervalNanos;
        this.nanoClock = nanoClock;
    }

    @Override
    public boolean getAsBoolean() {
        long now = this.nanoClock.getAsLong();
        if (!this.asked || now - this.askedAt >= this.intervalNanos) {
            this.value = this.source.getAsBoolean();
            this.askedAt = now;
            this.asked = true;
        }
        return this.value;
    }
}

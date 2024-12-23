package org.orecruncher.dsurround.lib.system;

import java.time.Instant;

public final class SystemClock implements ISystemClock {

    public static final long NANOS_PER_SECOND = 1_000_000_000;
    private static final long EPOCH_NANOS = System.currentTimeMillis() * 1_000_000;
    private static final long NANO_START = System.nanoTime();
    private static final long OFFSET_NANOS = EPOCH_NANOS - NANO_START;

    @Override
    public long getUtcNanosNow() {
        return System.nanoTime() + OFFSET_NANOS;
    }

    @Override
    public Instant getUtcNow() {
        var now = this.getUtcNanosNow();
        return Instant.ofEpochSecond(now / NANOS_PER_SECOND, now % NANOS_PER_SECOND);
    }

    @Override
    public IStopwatch getStopwatch() {
        return new Stopwatch(this);
    }
}

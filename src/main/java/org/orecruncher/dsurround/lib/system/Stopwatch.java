package org.orecruncher.dsurround.lib.system;

import java.util.concurrent.TimeUnit;

final class Stopwatch implements IStopwatch {

    private final ISystemClock _systemClock;
    private long _timeMark;

    Stopwatch(ISystemClock clock) {
        this._systemClock = clock;
    }

    @Override
    public void reset() {
        this._timeMark = this._systemClock.getUtcNanosNow();
    }

    @Override
    public boolean isNew() {
        return this._timeMark == 0;
    }

    @Override
    public long elapsed(TimeUnit timeUnit) {
        if (this.isNew())
            return 0;
        return timeUnit.convert(this._systemClock.getUtcNanosNow() - this._timeMark, TimeUnit.NANOSECONDS);
    }
}

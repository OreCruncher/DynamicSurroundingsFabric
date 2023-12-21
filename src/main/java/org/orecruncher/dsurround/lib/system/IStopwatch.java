package org.orecruncher.dsurround.lib.system;

import java.util.concurrent.TimeUnit;

public interface IStopwatch {

    /**
     * Resets the stopwatch to 0.
     */
    void reset();

    /**
     * Indicates if the stopwatch has had reset() called on it prior.
     *
     * @return True if reset has been called, false otherwise.
     */
    boolean isNew();

    /**
     * Time duration since last reset.  Value is converted based on TimeUnit value.
     *
     * @return Elapsed time
     */
    long elapsed(TimeUnit timeUnit);

    /**
     * Time duration since last reset in nanoseconds.
     *
     * @return Elapsed time
     */
    default long elapsed() {
        return this.elapsed(TimeUnit.NANOSECONDS);
    }
}

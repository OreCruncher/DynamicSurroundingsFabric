// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.audio;

import java.util.function.LongSupplier;

/**
 * How much time sounds starting may spend calculating their effects, a window at a time. A sound's first calculation
 * runs on the thread that plays it, which waits for it: the sound engine's, which also decodes and streams the game's
 * sounds. One costs about half a millisecond, so a burst (an explosion, a farm of mobs) could hold that thread for tens
 * of milliseconds. Past the budget a sound starts without its effects and is calculated on the workers' next pass.
 * Shared by the threads that start sounds (the sound engine's, and voice chat's speakers).
 */
final class StartBudget {

    private final long windowNanos;
    private final long budgetNanos;
    private final LongSupplier clock;
    private long windowStart;
    private long spent;
    private boolean started;

    /**
     * @param windowNanos how long each window is
     * @param budgetNanos the time starts may spend in a window
     * @param clock       the time now, in nanoseconds
     */
    StartBudget(long windowNanos, long budgetNanos, LongSupplier clock) {
        this.windowNanos = windowNanos;
        this.budgetNanos = budgetNanos;
        this.clock = clock;
    }

    /**
     * Whether a sound starting now may calculate its effects: the current window's budget isn't spent yet. One that
     * may reports what it took with {@link #spend(long)}.
     */
    synchronized boolean mayCalculate() {
        long now = this.clock.getAsLong();
        if (!this.started || now - this.windowStart >= this.windowNanos) {
            this.started = true;
            this.windowStart = now;
            this.spent = 0;
        }
        return this.spent < this.budgetNanos;
    }

    /**
     * What a start's calculation took, counted against the current window
     */
    synchronized void spend(long nanos) {
        this.spent += nanos;
    }
}

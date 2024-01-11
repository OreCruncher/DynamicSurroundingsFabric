package org.orecruncher.dsurround.lib.system;

import org.orecruncher.dsurround.lib.di.Cacheable;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.platform.events.ClientState;

/**
 * Monotonically increasing tick count based on client ticks.
 */
@Cacheable
public final class TickCounter implements ITickCount {

    private long tickCount = 0;

    public TickCounter() {
        ClientState.TICK_START.register(client -> this.tickCount++, HandlerPriority.VERY_HIGH);
    }

    @Override
    public long getTickCount() {
        return this.tickCount;
    }

    @Override
    public String toString() {
        return "TickCounter[%d]".formatted(this.tickCount);
    }
}
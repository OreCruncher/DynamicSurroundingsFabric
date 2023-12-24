package org.orecruncher.dsurround.lib.system;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.orecruncher.dsurround.lib.platform.events.ClientState;

/**
 * Monotonically increasing tick count based on client ticks.
 */
@Environment(EnvType.CLIENT)
public final class TickCounter implements ITickCount {

    private static long tickCount = 0;

    public TickCounter() {
        ClientState.TICK_START.register(client -> tickCount++);
    }

    @Override
    public long getTickCount() {
        return tickCount;
    }
}
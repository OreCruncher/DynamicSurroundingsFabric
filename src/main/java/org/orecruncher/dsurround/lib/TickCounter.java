package org.orecruncher.dsurround.lib;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.orecruncher.dsurround.lib.infra.events.ClientState;

/**
 * Monotonically increasing tick count based on client ticks.
 */
@Environment(EnvType.CLIENT)
public final class TickCounter {

    private static long tickCount = 0;

    public static void register() {
        ClientState.TICK_START.register(client -> tickCount++);
    }

    public static long getTickCount() {
        return tickCount;
    }
}
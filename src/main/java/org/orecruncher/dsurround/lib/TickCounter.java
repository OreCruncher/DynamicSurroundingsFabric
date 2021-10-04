package org.orecruncher.dsurround.lib;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Monotonically increasing tick count based on client ticks.
 */
@Environment(EnvType.CLIENT)
public final class TickCounter {

    private static long tickCount = 0;

    private TickCounter() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(TickCounter::onClientTick);
    }

    private static void onClientTick(final MinecraftClient event) {
        tickCount++;
    }

    public static long getTickCount() {
        return tickCount;
    }
}
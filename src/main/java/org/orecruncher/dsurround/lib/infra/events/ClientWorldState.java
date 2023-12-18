package org.orecruncher.dsurround.lib.infra.events;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.world.ClientWorld;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Event handlers for World state.  These handlers are cleared when the server
 * stops.
 */
@Environment(EnvType.CLIENT)
public final class ClientWorldState {
    /**
     * Event raised at the beginning of a ServerWorld's tick cycle.
     */
    public static final IPhasedEvent<ClientWorld> TICK_START = EventingFactory.createPrioritizedEvent();
    /**
     * Event raised at the end of a ServerWorld's tick cycle.
     */
    public static final IPhasedEvent<ClientWorld> TICK_END = EventingFactory.createPrioritizedEvent();

    private ClientWorldState() {
    }

    public static void initialize() {
        ClientTickEvents.START_WORLD_TICK.register(TICK_START::raise);
        ClientTickEvents.END_WORLD_TICK.register(TICK_END::raise);

        // Clear all the event handlers when the server stops.
        ClientState.STOPPING.register(server -> {
            TICK_START.clear();
            TICK_END.clear();
        }, HandlerPriority.LOW);
    }
}
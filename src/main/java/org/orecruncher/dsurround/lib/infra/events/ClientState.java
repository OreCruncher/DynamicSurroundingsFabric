package org.orecruncher.dsurround.lib.infra.events;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Event handlers for Server state.
 */
@Environment(EnvType.CLIENT)
public final class ClientState {
    /**
     * Event raised when the Client has started.
     */
    public static final IPhasedEvent<MinecraftClient> STARTED = EventingFactory.createPrioritizedEvent();
    /**
    /**
     * Event raised when the Client is stopping.
     */
    public static final IPhasedEvent<MinecraftClient> STOPPING = EventingFactory.createPrioritizedEvent();
    /**
     * Event raised at the beginning of the Client tick cycle.
     */
    public static final IPhasedEvent<MinecraftClient> TICK_START = EventingFactory.createPrioritizedEvent();
    /**
     * Event raised at the end of the Client tick cycle.
     */
    public static final IPhasedEvent<MinecraftClient> TICK_END = EventingFactory.createPrioritizedEvent();
    /**
     * Event raised when the client connects to a server.
     */
    public static final IPhasedEvent<MinecraftClient> ON_CONNECT = EventingFactory.createPrioritizedEvent();
    /**
     * Event raised when the client disconnects from a server.
     */
    public static final IPhasedEvent<MinecraftClient> ON_DISCONNECT = EventingFactory.createPrioritizedEvent();

    private ClientState() {
    }

    public static void initialize() {
        ClientLifecycleEvents.CLIENT_STARTED.register(STARTED::raise);
        ClientLifecycleEvents.CLIENT_STOPPING.register(STOPPING::raise);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ON_CONNECT.raise(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ON_DISCONNECT.raise(client));

        STOPPING.register(server -> {
            // Clear out tick handlers when the client is stopping.
            TICK_START.clear();
            TICK_END.clear();
        }, HandlerPriority.VERY_HIGH);

        ClientTickEvents.START_CLIENT_TICK.register(TICK_START::raise);
        ClientTickEvents.END_CLIENT_TICK.register(TICK_END::raise);
    }
}

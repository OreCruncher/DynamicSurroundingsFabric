package org.orecruncher.dsurround.lib.infra.events;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.DynamicRegistryManager;
import org.orecruncher.dsurround.lib.events.EventingFactory;
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
    /**
     * Event raised when tags sync to the client
     */
    public static final IPhasedEvent<TagSyncEvent> TAG_SYNC = EventingFactory.createPrioritizedEvent();

    private ClientState() {
    }

    public static void initialize() {
        ClientLifecycleEvents.CLIENT_STARTED.register(STARTED::raise);
        ClientLifecycleEvents.CLIENT_STOPPING.register(STOPPING::raise);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ON_CONNECT.raise(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ON_DISCONNECT.raise(client));
        ClientTickEvents.START_CLIENT_TICK.register(TICK_START::raise);
        ClientTickEvents.END_CLIENT_TICK.register(TICK_END::raise);
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (client)
                TAG_SYNC.raise(new TagSyncEvent(registries));
        });
    }

    public record TagSyncEvent(DynamicRegistryManager registries) {

    };
}

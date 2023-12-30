package org.orecruncher.dsurround.lib.platform.events;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.DynamicRegistryManager;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Event handlers for Server state.
 */
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

    public record TagSyncEvent(DynamicRegistryManager registries) {

    };
}

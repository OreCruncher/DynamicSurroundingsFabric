package org.orecruncher.dsurround.eventing;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceManager;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Event handlers for Client state.
 */
public final class ClientState {

    public static final IPhasedEvent<IClientStarted> CLIENT_START_EVENT = EventingFactory.createPrioritizedEvent();
    public static final IPhasedEvent<IClientStopping> CLIENT_STOP_EVENT = EventingFactory.createPrioritizedEvent();
    public static final IPhasedEvent<IClientTickStart> CLIENT_TICK_START_EVENT = EventingFactory.createPrioritizedEvent();
    public static final IPhasedEvent<IClientTickEnd> CLIENT_TICK_END_EVENT = EventingFactory.createPrioritizedEvent();
    public static final IPhasedEvent<IClientConnect> CLIENT_CONNECT_EVENT = EventingFactory.createPrioritizedEvent();
    public static final IPhasedEvent<IClientDisconnect> CLIENT_DISCONNECT_EVENT = EventingFactory.createPrioritizedEvent();
    public static final IPhasedEvent<ITagSync> TAG_SYNC_EVENT = EventingFactory.createPrioritizedEvent();
    public static final IPhasedEvent<IResourceReload> RESOURCE_RELOAD_EVENT = EventingFactory.createPrioritizedEvent();

    /**
     * Event raised when the client is starting
     */
    @FunctionalInterface
    public interface IClientStarted {
        void onStart(Minecraft client);
    }

    /**
     * Event raised when the Client is stopping.
     */
    @FunctionalInterface
    public interface IClientStopping {
        void onStopping(Minecraft client);
    }

    /**
     * Event raised at the beginning of the Client tick cycle.
     */
    @FunctionalInterface
    public interface IClientTickStart {
        void onTickStart(Minecraft client);
    }

    /**
     * Event raised at the end of the Client tick cycle.
     */
    @FunctionalInterface
    public interface IClientTickEnd {
        void onTickEnd(Minecraft client);
    }

    /**
     * Event raised when the client connects to a server.
     */
    @FunctionalInterface
    public interface IClientConnect {
        void onConnect(Minecraft client);
    }

    /**
     * Event raised when the client disconnects from a server.
     */
    @FunctionalInterface
    public interface IClientDisconnect {
        void onDisconnect(Minecraft client);
    }

    /**
     * Event raised when tags sync to the client
     */
    @FunctionalInterface
    public interface ITagSync {
        void onTagSync(RegistryAccess registryAccess);
    }

    /**
     * Event raised when resources reload
     */
    @FunctionalInterface
    public interface IResourceReload {
        void onResourceReload(ResourceManager resourceManager);
    }

    private ClientState() {
    }

    static {
        // Register with Architectury for known client side events
        ClientTickEvent.CLIENT_PRE.register(mc -> ClientState.CLIENT_TICK_START_EVENT.invoker().onTickStart(mc));
        ClientTickEvent.CLIENT_POST.register(mc -> ClientState.CLIENT_TICK_END_EVENT.invoker().onTickEnd(mc));

        ClientLifecycleEvent.CLIENT_STARTED.register(mc -> ClientState.CLIENT_START_EVENT.invoker().onStart(mc));
        ClientLifecycleEvent.CLIENT_STOPPING.register(mc -> ClientState.CLIENT_STOP_EVENT.invoker().onStopping(mc));

        // Connection detection is the first thing that processes, period.
        CLIENT_TICK_START_EVENT.register(ClientState::connectionDetector, HandlerPriority.VERY_HIGH);
    }

    private static boolean isConnected = false;
    private static void connectionDetector(Minecraft client) {
        // Basically, the logic will toggle isConnected based on whether a player instance
        // is present in the Minecraft client instance. Since this is a 100% client side,
        // the presence of the player instance can be used as a signal as to when the
        // client successfully connects to a server. If the player instance goes away, such
        // as a disconnect or a BungeeCord server transfer, the disconnect event will be fired
        // so dependent logic can clean up.
        if (isConnected) {
            if (client.player == null) {
                isConnected = false;
                Library.LOGGER.info("Player instance no longer present");
                CLIENT_DISCONNECT_EVENT.invoker().onDisconnect(client);
            }
        } else {
            if (client.player != null) {
                isConnected = true;
                Library.LOGGER.info("Player instance is now present");
                CLIENT_CONNECT_EVENT.invoker().onConnect(client);
            }
        }
    }
}
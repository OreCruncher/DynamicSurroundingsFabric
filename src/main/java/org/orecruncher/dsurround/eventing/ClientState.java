package org.orecruncher.dsurround.eventing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Event handlers for Server state.
 */
public final class ClientState {
    /**
     * Event raised when the Client has started.
     */
    public static final IPhasedEvent<IClientStarted> STARTED = EventingFactory.createPrioritizedEvent(callbacks -> client -> {
        for (var callback : callbacks) {
            callback.onStart(client);
        }
    });

    /**
    /**
     * Event raised when the Client is stopping.
     */
    public static final IPhasedEvent<IClientStopping> STOPPING = EventingFactory.createPrioritizedEvent(callbacks -> client -> {
        for (var callback : callbacks) {
            callback.onStopping(client);
        }
    });

    /**
     * Event raised at the beginning of the Client tick cycle.
     */
    public static final IPhasedEvent<IClientTickStart> TICK_START = EventingFactory.createPrioritizedEvent(callbacks -> client -> {
        for (var callback : callbacks) {
            callback.onTickStart(client);
        }
    });

    /**
     * Event raised at the end of the Client tick cycle.
     */
    public static final IPhasedEvent<IClientTickEnd> TICK_END = EventingFactory.createPrioritizedEvent(callbacks -> client -> {
        for (var callback : callbacks) {
            callback.onTickEnd(client);
        }
    });

    /**
     * Event raised when the client connects to a server.
     */
    public static final IPhasedEvent<IClientConnect> ON_CONNECT = EventingFactory.createPrioritizedEvent(callbacks -> client -> {
        for (var callback : callbacks) {
            callback.onConnect(client);
        }
    });

    /**
     * Event raised when the client disconnects from a server.
     */
    public static final IPhasedEvent<IClientDisconnect> ON_DISCONNECT = EventingFactory.createPrioritizedEvent(callbacks -> client -> {
        for (var callback : callbacks) {
            callback.onDisconnect(client);
        }
    });

    /**
     * Event raised when tags sync to the client
     */
    public static final IPhasedEvent<ITagSync> TAG_SYNC = EventingFactory.createPrioritizedEvent(callbacks -> registryAccess -> {
        for (var callback : callbacks) {
            callback.onTagSync(registryAccess);
        }
    });

    private ClientState() {
    }


    static {
        TICK_START.register(ClientState::connectionDetector, HandlerPriority.VERY_HIGH);
    }

    private static boolean isConnected = false;
    private static void connectionDetector(Minecraft client) {
        if(isConnected) {
            if (GameUtils.getPlayer().isEmpty()) {
                isConnected = false;
                Library.LOGGER.info("Disconnect detected");
                ON_DISCONNECT.raise().onDisconnect(client);
            }
        } else {
            if (GameUtils.getPlayer().isPresent()) {
                isConnected = true;
                Library.LOGGER.info("Connect detected");
                ON_CONNECT.raise().onConnect(client);
            }
        }
    }

    @FunctionalInterface
    public interface IClientStarted {
        void onStart(Minecraft client);
    }

    @FunctionalInterface
    public interface IClientStopping {
        void onStopping(Minecraft client);
    }

    @FunctionalInterface
    public interface IClientTickStart {
        void onTickStart(Minecraft client);
    }

    @FunctionalInterface
    public interface IClientTickEnd {
        void onTickEnd(Minecraft client);
    }

    @FunctionalInterface
    public interface IClientConnect {
        void onConnect(Minecraft client);
    }

    @FunctionalInterface
    public interface IClientDisconnect {
        void onDisconnect(Minecraft client);
    }

    @FunctionalInterface
    public interface ITagSync {
        void onTagSync(RegistryAccess registryAccess);
    }

}

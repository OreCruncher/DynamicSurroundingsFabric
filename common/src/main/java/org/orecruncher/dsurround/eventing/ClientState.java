package org.orecruncher.dsurround.eventing;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.events.HandlerPriority;

/**
 * Connects the platform's client events (through Architectury) to the mod's: tick start and end, client started and
 * stopping. Also raises {@link IClientConnect} and {@link IClientDisconnect}, by watching for the player instance at
 * the start of each tick.
 * <p>
 * {@link #initialize()} must be called during mod initialization, before the client starts.
 */
public final class ClientState {

    private static boolean initialized = false;
    private static boolean isConnected = false;

    private ClientState() {
    }

    /**
     * Hooks up the platform events. Calling it again does nothing.
     */
    public static synchronized void initialize() {
        if (initialized)
            return;
        initialized = true;

        ClientTickEvent.CLIENT_PRE.register(mc -> IClientTickStart.EVENT.invoker().onTickStart(mc));
        ClientTickEvent.CLIENT_POST.register(mc -> IClientTickEnd.EVENT.invoker().onTickEnd(mc));

        ClientLifecycleEvent.CLIENT_STARTED.register(mc -> IClientStarted.EVENT.invoker().onStart(mc));
        ClientLifecycleEvent.CLIENT_STOPPING.register(mc -> IClientStopping.EVENT.invoker().onStopping(mc));

        // Connection detection is the first thing that processes, period.
        IClientTickStart.EVENT.register(ClientState::connectionDetector, HandlerPriority.VERY_HIGH);
    }

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
                IClientDisconnect.EVENT.invoker().onDisconnect(client);
            }
        } else {
            if (client.player != null) {
                isConnected = true;
                Library.LOGGER.info("Player instance is now present");
                IClientConnect.EVENT.invoker().onConnect(client);
            }
        }
    }
}

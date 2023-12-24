package org.orecruncher.dsurround.lib.platform.services.fabric;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import org.orecruncher.dsurround.lib.platform.IEventRegistrations;
import org.orecruncher.dsurround.lib.platform.events.ClientState;

import java.util.function.Consumer;

public class EventRegistrationsImpl implements IEventRegistrations {

    public void register() {
        ClientLifecycleEvents.CLIENT_STARTED.register(ClientState.STARTED::raise);
        ClientLifecycleEvents.CLIENT_STOPPING.register(ClientState.STOPPING::raise);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientState.ON_CONNECT.raise(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.ON_DISCONNECT.raise(client));
        ClientTickEvents.START_CLIENT_TICK.register(ClientState.TICK_START::raise);
        ClientTickEvents.END_CLIENT_TICK.register(ClientState.TICK_END::raise);
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (client)
                ClientState.TAG_SYNC.raise(new ClientState.TagSyncEvent(registries));
        });
    }

    public void registerClientTickStart(Consumer<MinecraftClient> handler) {
        ClientTickEvents.START_CLIENT_TICK.register(handler::accept);
    }

    public void registerClientTickEnd(Consumer<MinecraftClient> handler) {
        ClientTickEvents.END_CLIENT_TICK.register(handler::accept);
    }
}

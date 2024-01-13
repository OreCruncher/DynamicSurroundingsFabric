package org.orecruncher.dsurround.fabric.services;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.lib.platform.IClientEventRegistrations;
import org.orecruncher.dsurround.lib.platform.events.ClientState;

import java.util.function.Consumer;

public class ClientEventRegistrationsImpl implements IClientEventRegistrations {

    public void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientState.ON_CONNECT.raise(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.ON_DISCONNECT.raise(client));
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (client)
                ClientState.TAG_SYNC.raise(new ClientState.TagSyncEvent(registries));
        });
    }
}

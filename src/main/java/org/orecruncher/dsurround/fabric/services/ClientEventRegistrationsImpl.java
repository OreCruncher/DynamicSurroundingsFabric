package org.orecruncher.dsurround.fabric.services;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.orecruncher.dsurround.lib.platform.IClientEventRegistrations;
import org.orecruncher.dsurround.lib.platform.events.ClientState;

public class ClientEventRegistrationsImpl implements IClientEventRegistrations {

    public void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientState.ON_CONNECT.raise(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.ON_DISCONNECT.raise(client));
    }
}

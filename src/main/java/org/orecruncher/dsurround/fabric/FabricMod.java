package org.orecruncher.dsurround.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.fabric.commands.Commands;

/**
 * Implements the Fabric specific binding to initialize the mod
 */
public final class FabricMod implements ClientModInitializer {

    private final Client client;

    public FabricMod() {
        this.client = new Client();
    }

    @Override
    public void onInitializeClient() {

        // Boot the mod
        this.client.initializeClient();

        // Fabric specific registrations
        ClientCommandRegistrationCallback.EVENT.register(Commands::register);
    }
}

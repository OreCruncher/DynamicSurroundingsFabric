package org.orecruncher.dsurround.platform.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.platform.fabric.commands.Commands;

/**
 * Implements the Fabric specific binding to initialize the mod
 */
public class FabricMod extends Client implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        // Boot the mod
        this.initializeClient();

        // Fabric specific registrations
        ClientCommandRegistrationCallback.EVENT.register(Commands::register);
    }
}

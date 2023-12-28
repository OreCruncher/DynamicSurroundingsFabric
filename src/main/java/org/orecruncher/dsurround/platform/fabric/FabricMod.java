package org.orecruncher.dsurround.platform.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.IClientEventRegistrations;
import org.orecruncher.dsurround.lib.platform.IPlatform;
import org.orecruncher.dsurround.platform.fabric.commands.Commands;
import org.orecruncher.dsurround.platform.fabric.services.ClientEventRegistrationsImpl;
import org.orecruncher.dsurround.platform.fabric.services.PlatformServiceImpl;

/**
 * Implements the Fabric specific binding to initialize the mod
 */
public class FabricMod extends Client implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Set up the platform environment
        ContainerManager.getRootContainer()
                .registerSingleton(IClientEventRegistrations.class, ClientEventRegistrationsImpl.class)
                .registerSingleton(IPlatform.class, PlatformServiceImpl.class);

        // Boot the mod
        this.initializeClient();

        // Fabric specific registrations
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> Commands.register(dispatcher));
    }
}

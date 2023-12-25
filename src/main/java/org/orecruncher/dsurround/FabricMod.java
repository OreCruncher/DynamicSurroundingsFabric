package org.orecruncher.dsurround;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.orecruncher.dsurround.commands.Commands;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.IEventRegistrations;
import org.orecruncher.dsurround.lib.platform.IPlatform;
import org.orecruncher.dsurround.lib.platform.ITagUtilities;
import org.orecruncher.dsurround.lib.platform.services.fabric.EventRegistrationsImpl;
import org.orecruncher.dsurround.lib.platform.services.fabric.PlatformServiceImpl;
import org.orecruncher.dsurround.lib.platform.services.fabric.TagUtilitiesImpl;

/**
 * Implements the Fabric specific binding to initialize the mod
 */
public class FabricMod extends Client implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Set up the platform environment
        ContainerManager.getDefaultContainer()
                .registerSingleton(IEventRegistrations.class, EventRegistrationsImpl.class)
                .registerSingleton(IPlatform.class, PlatformServiceImpl.class)
                .registerSingleton(ITagUtilities.class, TagUtilitiesImpl.class);

        // Boot the mod
        this.initializeClient();

        // Fabric specific registrations
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> Commands.register(dispatcher));
    }
}

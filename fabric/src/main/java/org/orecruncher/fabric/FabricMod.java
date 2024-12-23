package org.orecruncher.fabric;

import net.fabricmc.api.ClientModInitializer;
import org.orecruncher.dsurround.Client;

/**
 * Implements the Fabric specific binding to initialize the mod
 */
public final class FabricMod implements ClientModInitializer {

    private final Client client;

    public FabricMod() {
        this.client = new Client();
        this.client.construct();
    }

    @Override
    public void onInitializeClient() {
        // Boot the mod
        this.client.initializeClient();
    }
}

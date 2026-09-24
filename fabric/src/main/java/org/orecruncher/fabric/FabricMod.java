package org.orecruncher.fabric;

import org.orecruncher.dsurround.Client;

/**
 * Implements the Fabric specific binding to initialize the mod
 */
public final class FabricMod {

    public static void initialize() {
        Client.initialize();
    }

    public static void initializeClient() {
        Client.initializeClient();
    }
}

// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

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

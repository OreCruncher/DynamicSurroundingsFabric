// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.testing;

import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;

/**
 * Gives items their default components in tests. Since 26.x an item's components are bound once the registries are
 * loaded (the server does it with its reloadable resources), so a bootstrapped game alone has items with none:
 * "Components not bound yet". Call after Bootstrap.bootStrap; only the first call does anything.
 */
public final class ItemComponents {

    private static boolean bound;

    private ItemComponents() {
    }

    public static synchronized void bind() {
        if (bound)
            return;
        var lookup = VanillaRegistries.createReloadableLookup(VanillaRegistries.createWorldLookup());
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(DataComponentInitializers.PendingComponents::apply);
        bound = true;
    }
}

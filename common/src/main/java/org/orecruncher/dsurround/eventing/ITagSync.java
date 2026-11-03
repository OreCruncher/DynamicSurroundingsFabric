// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.eventing;

import net.minecraft.core.RegistryAccess;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Event raised when tags sync to the client
 */
@GenerateInvoker
@FunctionalInterface
public interface ITagSync {

    IPhasedEvent<ITagSync> EVENT = EventingFactory.createPrioritizedEvent(ITagSyncInvoker::create);

    void onTagSync(RegistryAccess registryAccess);
}

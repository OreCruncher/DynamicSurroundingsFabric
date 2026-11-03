// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.eventing;

import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Used to collect diagnostic information for display in the debug HUD
 */
@GenerateInvoker
@FunctionalInterface
public interface ICollectDiagnostics {

    IPhasedEvent<ICollectDiagnostics> EVENT = EventingFactory.createPrioritizedEvent(ICollectDiagnosticsInvoker::create);

    void onCollect(CollectDiagnosticsEvent event);
}

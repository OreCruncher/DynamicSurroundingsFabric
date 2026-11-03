// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.eventing;

import net.minecraft.client.renderer.FogRenderer;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Fired when fog is about to be rendered.
 */
@GenerateInvoker
@FunctionalInterface
public interface IFogRender {

    IPhasedEvent<IFogRender> EVENT = EventingFactory.createPrioritizedEvent(IFogRenderInvoker::create);

    void onRenderFog(FogRenderer.FogData data, float renderDistance, float partialTick);
}

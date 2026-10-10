package org.orecruncher.dsurround.eventing;

import net.minecraft.client.renderer.fog.FogData;
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

    void onRenderFog(FogData data, float renderDistance, float partialTick);
}

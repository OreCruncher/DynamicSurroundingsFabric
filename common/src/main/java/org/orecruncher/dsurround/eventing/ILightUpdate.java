package org.orecruncher.dsurround.eventing;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Fired when the client's light engine has changed the block or sky light in a section of the level, as vanilla is told
 * to draw the section again. Light changes some time after the block change that caused it, and spreads beyond it.
 * Fired often while light is being worked out (a section and layer at a time), so listeners should be cheap.
 */
@GenerateInvoker
@FunctionalInterface
public interface ILightUpdate {

    IPhasedEvent<ILightUpdate> EVENT = EventingFactory.createPrioritizedEvent(ILightUpdateInvoker::create);

    void onLightUpdate(LightLayer layer, SectionPos section);
}

package org.orecruncher.dsurround.eventing;

import net.minecraft.server.packs.resources.ResourceManager;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Event raised when resources reload
 */
@GenerateInvoker
@FunctionalInterface
public interface IResourceReload {

    IPhasedEvent<IResourceReload> EVENT = EventingFactory.createPrioritizedEvent(IResourceReloadInvoker::create);

    void onResourceReload(ResourceManager resourceManager);
}

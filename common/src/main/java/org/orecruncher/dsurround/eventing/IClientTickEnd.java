package org.orecruncher.dsurround.eventing;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Event raised at the end of the Client tick cycle.
 */
@GenerateInvoker
@FunctionalInterface
public interface IClientTickEnd {

    IPhasedEvent<IClientTickEnd> EVENT = EventingFactory.createPrioritizedEvent(IClientTickEndInvoker::create);

    void onTickEnd(Minecraft client);
}

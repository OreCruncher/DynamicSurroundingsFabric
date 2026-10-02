package org.orecruncher.dsurround.eventing;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Event raised at the beginning of the Client tick cycle.
 */
@GenerateInvoker
@FunctionalInterface
public interface IClientTickStart {

    IPhasedEvent<IClientTickStart> EVENT = EventingFactory.createPrioritizedEvent(IClientTickStartInvoker::create);

    void onTickStart(Minecraft client);
}

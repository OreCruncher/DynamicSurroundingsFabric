package org.orecruncher.dsurround.eventing;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Event raised when the client is starting
 */
@GenerateInvoker
@FunctionalInterface
public interface IClientStarted {

    IPhasedEvent<IClientStarted> EVENT = EventingFactory.createPrioritizedEvent(IClientStartedInvoker::create);

    void onStart(Minecraft client);
}

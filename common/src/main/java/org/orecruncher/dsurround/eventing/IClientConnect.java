package org.orecruncher.dsurround.eventing;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Event raised when the client connects to a server.
 */
@GenerateInvoker
@FunctionalInterface
public interface IClientConnect {

    IPhasedEvent<IClientConnect> EVENT = EventingFactory.createPrioritizedEvent(IClientConnectInvoker::create);

    void onConnect(Minecraft client);
}

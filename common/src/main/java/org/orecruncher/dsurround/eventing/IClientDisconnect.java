package org.orecruncher.dsurround.eventing;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Event raised when the client disconnects from a server.
 */
@GenerateInvoker
@FunctionalInterface
public interface IClientDisconnect {

    IPhasedEvent<IClientDisconnect> EVENT = EventingFactory.createPrioritizedEvent(IClientDisconnectInvoker::create);

    void onDisconnect(Minecraft client);
}

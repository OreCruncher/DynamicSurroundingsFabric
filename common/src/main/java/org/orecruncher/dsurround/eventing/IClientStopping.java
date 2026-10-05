package org.orecruncher.dsurround.eventing;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Event raised when the Client is stopping.
 */
@GenerateInvoker
@FunctionalInterface
public interface IClientStopping {

    IPhasedEvent<IClientStopping> EVENT = EventingFactory.createPrioritizedEvent(IClientStoppingInvoker::create);

    void onStopping(Minecraft client);
}

package org.orecruncher.dsurround.eventing;

import net.minecraft.client.multiplayer.ClientLevel;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Event raised when the client loads a level: joining a world, or changing dimension within one.
 */
@GenerateInvoker
@FunctionalInterface
public interface IClientLevelLoad {

    IPhasedEvent<IClientLevelLoad> EVENT = EventingFactory.createPrioritizedEvent(IClientLevelLoadInvoker::create);

    void onLevelLoad(ClientLevel level);
}

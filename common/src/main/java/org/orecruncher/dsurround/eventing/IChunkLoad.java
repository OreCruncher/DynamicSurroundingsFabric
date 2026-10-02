package org.orecruncher.dsurround.eventing;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Fired on the client thread when a chunk's data has arrived from the server and been loaded into the client
 * level. Also fired if the server sends a chunk again.
 */
@GenerateInvoker
@FunctionalInterface
public interface IChunkLoad {

    IPhasedEvent<IChunkLoad> EVENT = EventingFactory.createPrioritizedEvent(IChunkLoadInvoker::create);

    void onChunkLoad(ClientLevel level, ChunkPos chunkPos);
}

package org.orecruncher.dsurround.eventing;

import net.minecraft.core.BlockPos;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

import java.util.Collection;

/**
 * Fired when block state updates are received clientside.  Results are coalesced for efficiency.
 */
@GenerateInvoker
@FunctionalInterface
public interface IBlockUpdates {

    IPhasedEvent<IBlockUpdates> EVENT = EventingFactory.createPrioritizedEvent(IBlockUpdatesInvoker::create);

    void onBlockUpdates(Collection<BlockPos> blockPositions);
}

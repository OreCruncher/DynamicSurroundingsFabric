package org.orecruncher.dsurround.eventing;

import it.unimi.dsi.fastutil.longs.LongCollection;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Fired when block state updates are received clientside.  Results are coalesced for efficiency.
 * <p>
 * Positions are packed with {@link net.minecraft.core.BlockPos#asLong}. The collection is reused, so it is only valid
 * during the call: copy anything that needs keeping.
 */
@GenerateInvoker
@FunctionalInterface
public interface IBlockUpdates {

    IPhasedEvent<IBlockUpdates> EVENT = EventingFactory.createPrioritizedEvent(IBlockUpdatesInvoker::create);

    void onBlockUpdates(LongCollection blockPositions);
}

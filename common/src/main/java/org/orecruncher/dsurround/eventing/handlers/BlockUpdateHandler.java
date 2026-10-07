package org.orecruncher.dsurround.eventing.handlers;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongCollections;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.eventing.IBlockUpdates;
import org.orecruncher.dsurround.eventing.IClientTickEnd;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.threading.IClientTasking;

/**
 * Handles and tracks incoming block updates for the client world. Because this is client side, the logic will also
 * queue updates for the other blocks surrounding the indicated block. As the underlying tracking mechanism is
 * a hash set, positions are automatically deduplicated.
 */
public class BlockUpdateHandler {

    private static final IClientTasking CLIENT_TASKING = ContainerManager.resolve(IClientTasking.class);
    private static final int INITIAL_CAPACITY = 4 * 1024;
    private static final LongOpenHashSet updatedPositions = new LongOpenHashSet(INITIAL_CAPACITY);
    // What listeners get, so they can't change the set
    private static final LongCollection UPDATES_VIEW = LongCollections.unmodifiable(updatedPositions);

    static {
        IClientTickEnd.EVENT.register(BlockUpdateHandler::tick);
    }

    /**
     * Called from a mixin to record that a block position was updated. Note that the mixin logic checks
     * the level to ensure it is the client side (isClientSide()).
     *
     * @param world The level for which the event was raised
     * @param pos Block position that has been updated
     * @param oldState The state that is being replaced
     * @param newState The new incoming state
     */
    public static void blockPositionUpdate(ClientLevel world, BlockPos pos, BlockState oldState, BlockState newState) {
        // This routine should be invoked on the client thread, but in the off chance that some mod is doing
        // something strange, we need to protect ourselves.
        if (GameUtils.getMC().isSameThread()) {
            // We are on the client thread - fast path
            addPosition(pos);
        } else {
            // Not on client thread; queue it for the client thread. No need to wait: positions are only read at the
            // end of the client tick.
            CLIENT_TASKING.submit(() -> {
                Library.LOGGER.debug("blockPositionUpdate invoked from non-client thread!");
                addPosition(pos);
            });
        }
    }

    /**
     * Called at the tail end of a tick once all updates have been received and
     * processed by the client.
     *
     * @param ignored MinecraftClient instance - ignored
     */
    private static void tick(Minecraft ignored) {
        if (updatedPositions.isEmpty())
            return;

        var count = updatedPositions.size();
        IBlockUpdates.EVENT.invoker().onBlockUpdates(UPDATES_VIEW);

        // Have to clear for the next run. A burst grows the table, and clear() wipes all of it, so shrink it back.
        updatedPositions.clear();
        if (count > INITIAL_CAPACITY)
            updatedPositions.trim(INITIAL_CAPACITY);
    }

    private static void addPosition(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    updatedPositions.add(BlockPos.asLong(x + dx, y + dy, z + dz));
    }
}

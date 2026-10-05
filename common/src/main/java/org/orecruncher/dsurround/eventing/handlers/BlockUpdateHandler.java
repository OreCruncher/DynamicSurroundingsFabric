package org.orecruncher.dsurround.eventing.handlers;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
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

import java.util.Collection;
import java.util.Optional;

/**
 * Handles and tracks incoming block updates for the client world. Because this is client side, the logic will also
 * queue updates for the other blocks surrounding the indicated block. As the underlying tracking mechanism is
 * a hash set, positions are automatically deduplicated.
 */
public class BlockUpdateHandler {

    private static final IClientTasking CLIENT_TASKING = ContainerManager.resolve(IClientTasking.class);
    private static final LongSet updatedPositions = new LongOpenHashSet(4 * 1024);

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
        var updates = expand();
        updates.ifPresent(positions -> IBlockUpdates.EVENT.invoker().onBlockUpdates(positions));
    }

    private static void addPosition(BlockPos pos) {
        for (var offset : BlockPos.betweenClosed(-1, -1, -1, 1, 1, 1)) {
            updatedPositions.add(BlockPos.asLong(pos.getX() + offset.getX(), pos.getY() + offset.getY(), pos.getZ() + offset.getZ()));
        }
    }

    private static Optional<Collection<BlockPos>> expand() {
        if (updatedPositions.isEmpty())
            return Optional.empty();

        // Get the positions in the list as BlockPos instances
        var result = updatedPositions.longStream().mapToObj(BlockPos::of).toList();

        // Have to clear for the next run
        updatedPositions.clear();
        return Optional.of(result);
    }
}

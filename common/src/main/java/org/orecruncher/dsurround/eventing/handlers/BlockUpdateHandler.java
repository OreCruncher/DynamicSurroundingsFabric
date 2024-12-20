package org.orecruncher.dsurround.eventing.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.eventing.ClientState;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class BlockUpdateHandler {

    private static final Set<BlockPos> updatedPositions = new HashSet<>(16);
    private static final Set<BlockPos> expandedPositions = new HashSet<>(48);
    private static final Vec3i[] offsets = new Vec3i[27];

    static {
        int x = 0;
        for (int i = -1; i < 2; i++)
            for (int j = -1; j < 2; j++)
                for (int k = -1; k < 2; k++)
                    offsets[x++] = new Vec3i(i, j, k);

        ClientState.TICK_END.register(BlockUpdateHandler::tick);
    }

    /**
     * Called from a mixin to record that a block position was updated.
     *
     * @param pos Block position that has been updated
     */
    public static void blockPositionUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        updatedPositions.add(pos);
    }

    /**
     * Called at the tail end of a tick once all updates have been received and
     * processed by the client.
     *
     * @param ignored MinecraftClient instance - ignored
     */
    private static void tick(Minecraft ignored) {
        var updates = expand();
        updates.ifPresent(positions -> ClientEventHooks.BLOCK_UPDATE.raise().onBlockUpdates(positions));
    }

    private static Optional<Collection<BlockPos>> expand() {
        if (updatedPositions.isEmpty())
            return Optional.empty();

        // Need to expand out the updates to adjacent blocks.  A state change
        // of a block may affect how the adjacent blocks are handled. Can't rely on
        // neighbor state changes since the effects the mod produces are virtual
        // and do not exist server side.
        expandedPositions.clear();
        for (final BlockPos center : updatedPositions)
            for (final Vec3i offset : offsets)
                expandedPositions.add(center.offset(offset));

        // Have to clear for the next run
        updatedPositions.clear();
        return Optional.of(expandedPositions);
    }
}

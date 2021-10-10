package org.orecruncher.dsurround.eventing.handlers;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.eventing.ClientEventHooks;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class BlockUpdateHandler {

    private static final Set<BlockPos> updatedPositions = new HashSet<>(16);

    static {
        ClientTickEvents.END_CLIENT_TICK.register(BlockUpdateHandler::tick);
    }

    /**
     * Called from a mixin to record that a block position was updated.
     *
     * @param pos Block position that has been updated
     */
    public static void blockPositionUpdate(BlockPos pos) {
        updatedPositions.add(pos);
    }

    /**
     * Called at the tail end of a tick once all updates have been received and
     * processed by the client.
     *
     * @param ignored MinecraftClient instance - ignored
     */
    private static void tick(MinecraftClient ignored) {
        Collection<BlockPos> updates = expand();
        if (updates != null)
            ClientEventHooks.BLOCK_UPDATE.invoker().onUpdate(updates);
    }

    @Nullable
    private static Collection<BlockPos> expand() {
        if (updatedPositions.size() == 0)
            return null;

        // Need to expand out the updates to adjacent blocks.  A state change
        // of a block may affect how the adjacent blocks are handled.
        Set<BlockPos> updates = new ObjectOpenHashSet<>();
        for (final BlockPos center : updatedPositions) {
            for (int i = -1; i < 2; i++)
                for (int j = -1; j < 2; j++)
                    for (int k = -1; k < 2; k++)
                        updates.add(center.add(i, j, k));
        }

        // Have to clear for next run
        updatedPositions.clear();
        return updates;
    }
}

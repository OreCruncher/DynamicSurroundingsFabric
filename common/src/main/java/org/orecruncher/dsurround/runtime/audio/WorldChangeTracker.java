// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.audio;

import it.unimi.dsi.fastutil.longs.LongCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.orecruncher.dsurround.eventing.*;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts changes that can alter how sound travels: block updates and chunk loads near the player, library reloads
 * (block reflectivity and occlusion), configuration changes, and leaving a world. Ray traced sound results are reused
 * while the count is unchanged (see {@link TraceCache}).
 * <p>
 * Near is within {@link #NEAR} blocks. Each sound is calculated about three times a second, so a change anywhere ending
 * every result would mean almost none were reused: grass spreads, crops grow and water freezes somewhere all the time.
 * A change farther off than that could still reach a long reverb ray, and is too small a share of them to hear. A busy
 * place nearby (redstone, flowing water) reuses results less often, which only costs the work the cache would have saved.
 */
public final class WorldChangeTracker {

    static final int NEAR = 64;
    private static final AtomicLong GENERATION = new AtomicLong();
    private static boolean registered;
    // From a chunk's middle to its corner, in blocks, rounded up
    private static final double CHUNK_REACH = 12D;

    private WorldChangeTracker() {
    }

    /**
     * Hooks the events that count as changes. Calling it again does nothing.
     */
    public static synchronized void register() {
        if (registered)
            return;
        registered = true;
        IBlockUpdates.EVENT.register(WorldChangeTracker::onBlockUpdates);
        IChunkLoad.EVENT.register((level, chunkPos) -> onChunkLoad(chunkPos));
        IReloadEvent.EVENT.register((resources, scope) -> changed());
        IConfigChangedEvent.EVENT.register(config -> changed());
        IClientDisconnect.EVENT.register(client -> changed());
    }

    private static void onBlockUpdates(LongCollection positions) {
        var player = GameUtils.getPlayer().orElse(null);
        if (player == null) {
            changed();
            return;
        }
        var iterator = positions.longIterator();
        while (iterator.hasNext()) {
            long pos = iterator.nextLong();
            if (isNear(BlockPos.getX(pos) - player.getX(), BlockPos.getY(pos) - player.getY(), BlockPos.getZ(pos) - player.getZ())) {
                changed();
                return;
            }
        }
    }

    private static void onChunkLoad(ChunkPos chunk) {
        var player = GameUtils.getPlayer().orElse(null);
        if (player == null || isNearColumn(chunk.getMiddleBlockX() - player.getX(), chunk.getMiddleBlockZ() - player.getZ(), CHUNK_REACH))
            changed();
    }

    /**
     * Whether a change this far from the player, each way, can be heard in a sound's results
     */
    static boolean isNear(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz <= NEAR * NEAR;
    }

    /**
     * Whether a column this far from the player, each way, comes within {@link #NEAR} of them, with {@code reach} blocks
     * of it counted as well: a chunk's corners, from its middle. All the column's heights are taken as level with them.
     */
    static boolean isNearColumn(double dx, double dz, double reach) {
        double range = NEAR + reach;
        return dx * dx + dz * dz <= range * range;
    }

    /**
     * The current count. Read it before tracing, so a change during tracing is picked up next time.
     */
    public static long generation() {
        return GENERATION.get();
    }

    public static void changed() {
        GENERATION.incrementAndGet();
    }
}

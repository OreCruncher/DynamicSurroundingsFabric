package org.orecruncher.dsurround.lib.compat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public class LevelCompat {
    public static boolean isSuperFlat(final Level level) {
        return level instanceof ClientLevel cl && cl.getLevelData().isFlat;
    }

    public static BlockPos getTopSolidOrLiquidBlock(final Level level, final BlockPos pos) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
    }

    public static int getPrecipitationHeight(final Level level, final BlockPos pos) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    /**
     * Whether a block entity matching {@code predicate} is within {@code range} of {@code center}. Only the loaded
     * chunks that overlap the range are searched.
     */
    public static boolean doesBlockEntityExistNear(final Level level, final Vec3 center, final double range, final Predicate<BlockEntity> predicate) {
        final int minX = chunkCoord(center.x - range);
        final int maxX = chunkCoord(center.x + range);
        final int minZ = chunkCoord(center.z - range);
        final int maxZ = chunkCoord(center.z + range);
        var chunkSource = level.getChunkSource();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                var chunk = chunkSource.getChunk(cx, cz, false);
                if (chunk == null)
                    continue;
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity.getBlockPos().closerToCenterThan(center, range) && predicate.test(blockEntity))
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * The coordinate of the chunk that holds block coordinate {@code blockCoord}.
     */
    static int chunkCoord(final double blockCoord) {
        return SectionPos.blockToSectionCoord(Mth.floor(blockCoord));
    }

    public static boolean isChunkLoaded(final Level level, final BlockPos pos) {
        return level.isLoaded(pos);
    }

}

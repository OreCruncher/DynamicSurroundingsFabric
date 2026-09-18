package org.orecruncher.dsurround.lib.compat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;

import java.util.function.Predicate;

public class LevelCompat {
    public static boolean isSuperFlat(final Level level) {
        return ReflectionHelper.cast(level, ClientLevel.class)
                .map(cl -> cl.getLevelData().isFlat)
                .orElse(false);
    }

    public static BlockPos getTopSolidOrLiquidBlock(final Level level, final BlockPos pos) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
    }

    public static int getPrecipitationHeight(final Level level, final BlockPos pos) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    public static boolean doesBlockEntityExist(final ClientLevel level, final Predicate<BlockEntity> predicate) {
        var chunks = level.chunkSource.storage.chunks;
        for (int i = 0; i < chunks.length(); i++) {
            var chunk = chunks.get(i);
            if (chunk != null) {
                for (var blockEntity : chunk.getBlockEntities().entrySet()) {
                    if (predicate.test(blockEntity.getValue()))
                        return true;
                }
            }
        }
        return false;
    }

    public static boolean isChunkLoaded(final Level level, final BlockPos pos) {
        return level.isLoaded(pos);
    }

}

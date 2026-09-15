package org.orecruncher.dsurround.lib.compat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;
import org.orecruncher.dsurround.mixinutils.IClientWorld;

import java.util.function.Predicate;
import java.util.stream.Stream;

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

    public static boolean doesBlockEntityExist(Level level, Predicate<BlockEntity> predicate) {
        return ReflectionHelper.cast(level, IClientWorld.class)
                .map(IClientWorld::dsurround$getLoadedChunks)
                .orElse(Stream.empty())
                .flatMap(lc -> lc.getBlockEntities().values().stream())
                .anyMatch(predicate);
    }

    public static boolean isChunkLoaded(Level level, BlockPos pos) {
        return level.isLoaded(pos);
    }
}

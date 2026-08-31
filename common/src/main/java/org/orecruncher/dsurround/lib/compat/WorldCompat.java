package org.orecruncher.dsurround.lib.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;
import org.orecruncher.dsurround.mixins.core.MixinClientWorldProperties;
import org.orecruncher.dsurround.mixinutils.IClientWorld;

import java.util.function.Predicate;
import java.util.stream.Stream;

public class WorldCompat {
    public static boolean isSuperFlat(final Level world) {
        final LevelData info = world.getLevelData();
        return ReflectionHelper.cast(info, MixinClientWorldProperties.class)
                .map(MixinClientWorldProperties::dsurround_isFlatWorld)
                .orElse(false);
    }

    public static BlockPos getTopSolidOrLiquidBlock(final Level world, final BlockPos pos) {
        return world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
    }

    public static int getPrecipitationHeight(final Level world, final BlockPos pos) {
        return world.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    public static boolean doesBlockEntityExist(Level world, Predicate<BlockEntity> predicate) {
        return ReflectionHelper.cast(world, IClientWorld.class)
                .map(IClientWorld::dsurround_getLoadedChunks)
                .orElse(Stream.empty())
                .flatMap(lc -> lc.getBlockEntities().values().stream())
                .anyMatch(predicate);
    }

    public static boolean isChunkLoaded(Level world, BlockPos pos) {
        return world.isLoaded(pos);
    }
}

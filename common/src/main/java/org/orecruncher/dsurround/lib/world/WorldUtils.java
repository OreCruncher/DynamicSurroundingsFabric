package org.orecruncher.dsurround.lib.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import org.orecruncher.dsurround.mixins.core.MixinClientWorldProperties;
import org.orecruncher.dsurround.mixinutils.IClientWorld;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WorldUtils {
    public static boolean isSuperFlat(final Level world) {
        final LevelData info = world.getLevelData();
        return info instanceof MixinClientWorldProperties && ((MixinClientWorldProperties) info).dsurround_isFlatWorld();
    }

    public static BlockPos getTopSolidOrLiquidBlock(final Level world, final BlockPos pos) {
        return world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
    }

    public static int getPrecipitationHeight(final Level world, final BlockPos pos) {
        return world.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    public static List<BlockEntity> getLoadedBlockEntities(Level world, Predicate<BlockEntity> predicate) {
        var accessor = (IClientWorld) world;
        return accessor.dsurround_getLoadedChunks()
                .flatMap(chunk -> chunk.getBlockEntities().values().stream())
                .filter(predicate)
                .collect(Collectors.toList());
    }

    public static boolean isChunkLoaded(Level world, BlockPos pos) {
        return world.isLoaded(pos);
    }
}

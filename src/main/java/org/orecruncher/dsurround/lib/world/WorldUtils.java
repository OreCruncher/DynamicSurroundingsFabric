package org.orecruncher.dsurround.lib.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import org.orecruncher.dsurround.mixins.core.MixinClientWorldProperties;
import org.orecruncher.dsurround.mixinutils.IClientWorld;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WorldUtils {
    /**
     * Temperatures LESS than this value are considered cold temperatures.
     */
    public static final float COLD_THRESHOLD = 0.2F;

    /**
     * Temperatures LESS than this value are considered cold enough for snow.
     */
    public static final float SNOW_THRESHOLD = 0.15F;

    public static boolean isSuperFlat(final Level world) {
        final LevelData info = world.getLevelData();
        return info instanceof MixinClientWorldProperties && ((MixinClientWorldProperties) info).dsurround_isFlatWorld();
    }

    public static BlockPos getTopSolidOrLiquidBlock(final Level world, final BlockPos pos) {
        return world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
    }

    public static float getTemperatureAt(final Level world, final BlockPos pos) {
        return world.getBiome(pos).value().getBaseTemperature();
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

    /**
     * Gets the precipitation currently falling at the specified location.  It takes into account temperature and the
     * like.
     */
    public static Biome.Precipitation getCurrentPrecipitationAt(final Level world, final BlockPos pos) {
        if (!world.isRaining()) {
            // Not currently raining
            return Biome.Precipitation.NONE;
        }

        final Biome biome = world.getBiome(pos).value();

        // If the biome has no rain...
        if (biome.getPrecipitationAt(pos) == Biome.Precipitation.NONE)
            return Biome.Precipitation.NONE;

        // Is there a block above that is blocking the rainfall?
        var p = getPrecipitationHeight(world, pos);
        if (p > pos.getY()) {
            return Biome.Precipitation.NONE;
        }

        // Use the temperature of the biome to get whether it is raining or snowing
        final float temp = getTemperatureAt(world, pos);
        return isSnowTemperature(temp) ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
    }

    /**
     * Determines if the temperature value is considered a cold temperature.
     */
    public static boolean isColdTemperature(final float temp) {
        return temp < COLD_THRESHOLD;
    }

    /**
     * Determines if the temperature value is considered cold enough for snow.
     */
    public static boolean isSnowTemperature(final float temp) {
        return temp < SNOW_THRESHOLD;
    }

    public static boolean isChunkLoaded(Level world, BlockPos pos) {
        return world.isLoaded(pos);
    }
}

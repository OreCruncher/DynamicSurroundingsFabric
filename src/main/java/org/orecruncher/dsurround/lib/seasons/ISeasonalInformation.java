package org.orecruncher.dsurround.lib.seasons;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;

public interface ISeasonalInformation {

    /**
     * Name of the seasonal provider
     */
    String getProviderName();

    /**
     * Gets the name of the current season, if any
     */
    Optional<String> getCurrentSeason();

    /**
     * Gets the temperature at the specified block location taking into account any seasonal variance.
     */
    float getTemperature(Level world, BlockPos blockPos);

    /**
     * Indicates whether the temperature at the given position is considered cold. For example, if the temp
     * is cold, the frost breath effect can be produced.
     */
    default boolean isColdTemperature(Level world, BlockPos blockPos) {
        return this.getTemperature(world, blockPos) <= 0.2F;
    }

    /**
     * Indicates whether the temperature at the given position is considered cold enough for snow.
     */
    default boolean isSnowTemperature(Level world, BlockPos blockPos) {
        return this.getTemperature(world, blockPos) <= 0.15F;
    }

    /**
     * Gets the Y on the XZ plane at which precipitation will strike.
     */
    default int getPrecipitationHeight(Level world, BlockPos pos) {
        return world.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    /**
     * Gets the active precipitation occurring at the specified position.
     */
    default Biome.Precipitation getActivePrecipitation(Level world, BlockPos pos) {
        if (!world.isRaining()) {
            // Not currently raining
            return Biome.Precipitation.NONE;
        }

        final Biome biome = world.getBiome(pos).value();

        // If the biome has no rain...
        if (biome.getPrecipitationAt(pos) == Biome.Precipitation.NONE)
            return Biome.Precipitation.NONE;

        // Is there a block above that is blocking the rainfall?
        var p = this.getPrecipitationHeight(world, pos);
        if (p > pos.getY()) {
            return Biome.Precipitation.NONE;
        }

        // Use the temperature of the biome to get whether it is raining or snowing
        return this.isSnowTemperature(world, pos) ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
    }
}

package org.orecruncher.dsurround.lib.seasons;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
    Optional<Component> getCurrentSeason(Level world);

    /**
     * Gets the translated season name from the provider resources.
     */
    Optional<Component> getCurrentSeasonTranslated(Level world);

    /**
     * Indicates if the current season is considered Spring.
     */
    default boolean isSpring(Level world) {
        return true;
    }

    /**
     * Indicates if the current season is considered Summer.
     */
    default boolean isSummer(Level world) {
        return false;
    }

    /**
     * Indicates if the current season is considered Autumn/Fall.
     */
    default boolean isAutumn(Level world) {
        return false;
    }

    /**
     * Indicates if the current season is considered Winter.
     */
    default boolean isWinter(Level world) {
        return false;
    }

    /**
     * Gets the temperature at the specified block location taking into account any seasonal variance.
     */
    float getTemperature(Level world, BlockPos blockPos, int seaLevel);

    /**
     * Indicates whether the temperature at the given position is considered cold. For example, if the temp
     * is cold, the frost breath effect can be produced.
     */
    default boolean isColdTemperature(Level world, BlockPos blockPos, int seaLevel) {
        return this.getTemperature(world, blockPos, seaLevel) < 0.2F;
    }

    /**
     * Indicates whether the temperature at the given position is considered cold enough for snow.
     */
    default boolean isSnowTemperature(Level world, BlockPos blockPos, int seaLevel) {
        return this.getTemperature(world, blockPos, seaLevel) < 0.15F;
    }

    /**
     * Gets the Y on the XZ plane at which precipitation will strike.
     */
    default int getPrecipitationHeight(Level world, BlockPos pos) {
        return world.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    /**
     * Gets the possible precipitation that can occur in the biome at the specified position.
     */
    default Biome.Precipitation getPrecipitationAt(Level world, BlockPos blockPos, int seaLevel) {
        return world.getBiome(blockPos).value().getPrecipitationAt(blockPos, seaLevel);
    }

    /**
     * Gets the active precipitation occurring at the specified position.
     */
    default Biome.Precipitation getActivePrecipitation(Level world, BlockPos pos, int seaLevel) {
        if (!world.isRaining()) {
            // Not currently raining
            return Biome.Precipitation.NONE;
        }

        // If the biome has no rain...
        if (this.getPrecipitationAt(world, pos, seaLevel) == Biome.Precipitation.NONE)
            return Biome.Precipitation.NONE;

        // Is there a block above that is blocking the rainfall?
        var p = this.getPrecipitationHeight(world, pos);
        if (p > pos.getY()) {
            return Biome.Precipitation.NONE;
        }

        // Use the temperature of the biome to get whether it is raining or snowing
        return this.isSnowTemperature(world, pos, seaLevel) ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
    }
}

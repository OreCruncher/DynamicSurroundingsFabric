package org.orecruncher.dsurround.lib.seasons;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.Optional;

public interface ISeasonalInformation {

    /**
     * Name of the seasonal provider
     */
    String getProviderName();

    /**
     * Gets the name of the current season, if any
     */
    Optional<Component> getCurrentSeason();

    /**
     * Gets the translated season name from the provider resources.
     */
    Optional<Component> getCurrentSeasonTranslated();

    /**
     * Indicates if the current season is considered Spring.
     */
    default boolean isSpring() {
        return true;
    }

    /**
     * Indicates if the current season is considered Summer.
     */
    default boolean isSummer() {
        return false;
    }

    /**
     * Indicates if the current season is considered Autumn/Fall.
     */
    default boolean isAutumn() {
        return false;
    }

    /**
     * Indicates if the current season is considered Winter.
     */
    default boolean isWinter() {
        return false;
    }

    /**
     * Gets the temperature at the specified block location taking into account any seasonal variance.
     */
    float getTemperature(BlockPos blockPos);

    /**
     * Indicates whether the temperature at the given position is considered cold. For example, if the temp
     * is cold, the frost breath effect can be produced.
     */
    default boolean isColdTemperature(BlockPos blockPos) {
        return this.getTemperature(blockPos) < 0.2F;
    }

    /**
     * Indicates whether the temperature at the given position is considered cold enough for snow.
     */
    default boolean isSnowTemperature(BlockPos blockPos) {
        return this.getTemperature(blockPos) < 0.15F;
    }

    /**
     * Gets the Y on the XZ plane at which precipitation will strike.
     */
    default int getPrecipitationHeight(BlockPos pos) {
        return this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    /**
     * Gets the possible precipitation that can occur in the biome at the specified position.
     */
    default Biome.Precipitation getPrecipitationAt(BlockPos blockPos) {
        return this.level().getBiome(blockPos).value().getPrecipitationAt(blockPos);
    }

    /**
     * Gets the active precipitation occurring at the specified position.
     */
    default Biome.Precipitation getActivePrecipitation(BlockPos pos) {
        if (!this.level().isRaining()) {
            // Not currently raining
            return Biome.Precipitation.NONE;
        }

        // If the biome has no rain...
        if (this.getPrecipitationAt(pos) == Biome.Precipitation.NONE)
            return Biome.Precipitation.NONE;

        // Is there a block above that is blocking the rainfall?
        var p = this.getPrecipitationHeight(pos);
        if (p > pos.getY()) {
            return Biome.Precipitation.NONE;
        }

        // Use the temperature of the biome to get whether it is raining or snowing
        return this.isSnowTemperature(pos) ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
    }

    default ClientLevel level() {
        return GameUtils.getWorld().orElseThrow();
    }
}

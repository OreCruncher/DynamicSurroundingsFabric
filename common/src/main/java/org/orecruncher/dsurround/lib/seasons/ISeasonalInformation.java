package org.orecruncher.dsurround.lib.seasons;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;

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
    boolean isSpring();
    /**
     * Indicates if the current season is considered Summer.
     */
    boolean isSummer();
    /**
     * Indicates if the current season is considered Autumn/Fall.
     */
    boolean isAutumn();
    /**
     * Indicates if the current season is considered Winter.
     */
    boolean isWinter();
    /**
     * Indicates if the current season is in the earlier part.
     */
    boolean isEarly();
    /**
     * Indicates if the current season is in the middle part.
     */
    boolean isMiddle();
    /**
     * Indicates if the current season is in the late part.
     */
    boolean isLate();
    /**
     * Gets the temperature at the specified block location taking into account any seasonal variance.
     */
    float getTemperatureAt(BlockPos blockPos);
    /**
     * Indicates whether the temperature at the given position is considered cold. For example, if the temp
     * is cold, the frost breath effect can be produced.
     */
    boolean isColdTemperature(BlockPos blockPos);
    /**
     * Indicates whether the temperature at the given position is considered cold enough for snow.
     */
    boolean isSnowTemperature(BlockPos blockPos);
    /**
     * Gets the Y on the XZ plane at which precipitation will strike.
     */
    int getPrecipitationHeight(BlockPos pos);
    /**
     * Gets the possible precipitation that can occur in the biome at the specified position.
     */
    Biome.Precipitation getPrecipitationAt(BlockPos blockPos);
    /**
     * Gets the active precipitation occurring at the specified position.
     */
    Biome.Precipitation getActivePrecipitationAt(BlockPos pos);
    /**
     * Helper to access current ClientLevel
     */
    ClientLevel level();
}

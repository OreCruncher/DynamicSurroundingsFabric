// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.seasons;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

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
     * Whether a mod provides seasons. Without one, the season is always reported as spring.
     */
    default boolean hasSeasons() {
        return true;
    }

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
     * Below this temperature, precipitation falls as snow; at it and above, as rain. Vanilla's threshold.
     */
    float SNOW_TEMPERATURE = 0.15F;

    /**
     * Whether what falls at the position (where anything does) is snow rather than rain. Below
     * {@link #SNOW_TEMPERATURE} unless a seasons mod decides snow by itself (Ecliptic Seasons, by solar term: a warm
     * biome can snow in its winter).
     *
     * @param temperature {@link #getTemperatureAt} at the position, which the caller has already looked up
     */
    boolean snowsAt(BlockPos blockPos, float temperature);

    /**
     * Whether rain or snow falls at the position now, whichever it is: the biome's own setting, or its season's
     */
    boolean hasPrecipitationAt(BlockPos blockPos);
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
     * The heaviest the storm's rain or snow falls where the player is, 0 to 1: a seasons mod's lighter rain (Ecliptic
     * Seasons gives some solar terms a light or moderate rain). A weaker storm falls as it is. 1 by default, as vanilla
     * and Serene Seasons have none. Asked once a client tick, on the client thread.
     */
    default float getRainCap() {
        return 1F;
    }

    /**
     * Whether a seasons mod draws a cover of snow on top of the block, though the block itself is unchanged: Ecliptic
     * Seasons' snowy winter shows grass, leaves and the like snow-covered, {@link #SNOW_COVER} thick. False by default.
     */
    default boolean isSnowCovered(BlockPos blockPos, BlockState state) {
        return false;
    }

    /**
     * How thick a seasons mod's cover of snow is drawn on top of a block, in blocks (Ecliptic Seasons': 2 pixels)
     */
    double SNOW_COVER = 2D / 16D;

    /**
     * Helper to access current ClientLevel
     */
    ClientLevel level();
}

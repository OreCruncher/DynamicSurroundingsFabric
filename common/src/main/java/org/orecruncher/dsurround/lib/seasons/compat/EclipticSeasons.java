// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.seasons.compat;

import com.teamtea.eclipticseasons.api.EclipticSeasonsApi;
import com.teamtea.eclipticseasons.api.constant.solar.Season;
import com.teamtea.eclipticseasons.api.util.EclipticUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.eventing.IClientTickStart;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.function.CachingSupplier;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;

import java.util.Optional;

/**
 * Ecliptic Seasons, through its API ({@link EclipticSeasonsApi}): the year's seasons, each early, mid and late (two
 * of its 24 solar terms each), and the weather they bring. It decides snow by solar term rather than by temperature,
 * so a warm biome can snow in its winter: what falls where is its answer ({@link #snowsAt}), and the temperature
 * (its seasonal one) only gives frost, breath and sleet. Where seasons are off (another dimension, or turned off) it
 * answers as without a seasons mod: spring, each part of it.
 */
public class EclipticSeasons extends AbstractSeasonProvider {

    /**
     * The warmest it is where Ecliptic Seasons' season brings snow: under the snow line, so water freezes, and cold
     * enough for a frosty breath (under 0.2) and frost on windows
     */
    static final float SNOWING_TEMPERATURE = SNOW_TEMPERATURE - 0.05F;

    private final CachingSupplier<Season.Sub> subSeasonCache;
    // Set once EclipticUtil.getTemperatureFloat is found missing (see temperatureAt)
    private volatile boolean temperatureHookBroken;

    public EclipticSeasons(ILevelOracle levelOracle) {
        super("Ecliptic Seasons", levelOracle);
        this.subSeasonCache = CachingSupplier.from(() -> api().getSubSeason(this.level()));
        IClientTickStart.EVENT.register(ignored -> this.subSeasonCache.clear());
    }

    private static EclipticSeasonsApi api() {
        return EclipticSeasonsApi.getInstance();
    }

    @Override
    public boolean hasSeasons() {
        return api().isSeasonEnabled(this.level());
    }

    private Season.Sub subSeason() {
        return this.subSeasonCache.get();
    }

    @Override
    public Optional<Component> getCurrentSeason() {
        var subSeason = this.subSeason();
        return subSeason.isValid() ? Optional.of(Component.literal(subSeason.toString())) : Optional.empty();
    }

    @Override
    public Optional<Component> getCurrentSeasonTranslated() {
        var subSeason = this.subSeason();
        if (!subSeason.isValid())
            return Optional.empty();
        // The part of the season and its solar term, in Ecliptic Seasons' own words
        var solarTerm = api().getSolarTerm(this.level());
        return Optional.of(Component.translatable("%s (%s)", subSeason.getTranslation(), solarTerm.getTranslation()));
    }

    private boolean isSeason(Season season) {
        var current = this.subSeason().getSeason();
        // Without seasons, spring, as without a seasons mod
        return current == season || (current == Season.NONE && season == Season.SPRING);
    }

    private boolean isPart(int part) {
        var subSeason = this.subSeason();
        // EARLY, MID and LATE in turn for each season, so 0, 1 or 2 is the part; without seasons, each part, as
        // without a seasons mod
        return !subSeason.isValid() || subSeason.ordinal() % 3 == part;
    }

    @Override
    public boolean isSpring() {
        return this.isSeason(Season.SPRING);
    }

    @Override
    public boolean isSummer() {
        return this.isSeason(Season.SUMMER);
    }

    @Override
    public boolean isAutumn() {
        return this.isSeason(Season.AUTUMN);
    }

    @Override
    public boolean isWinter() {
        return this.isSeason(Season.WINTER);
    }

    @Override
    public boolean isEarly() {
        return this.isPart(0);
    }

    @Override
    public boolean isMiddle() {
        return this.isPart(1);
    }

    @Override
    public boolean isLate() {
        return this.isPart(2);
    }

    /**
     * The biome's temperature moved by the solar term (and warmer at a summer noon in the sun), from Ecliptic Seasons'
     * {@code EclipticUtil}, in its API package but not on the interface; no warmer than {@link #SNOWING_TEMPERATURE}
     * where its season brings snow. Its solar terms take at most 0.45 off a biome's temperature, so plains (0.8)
     * snow in deep winter at 0.4, too warm for frost or a frosty breath: snow means freezing here, as in vanilla.
     * Should a version move {@code getTemperatureFloat}, that is logged once and the biome's own temperature used from
     * then on, still freezing where it snows.
     */
    @Override
    protected float temperatureAt(BlockPos blockPos) {
        var temperature = this.seasonalTemperatureAt(blockPos);
        return this.precipitationAt(blockPos) == Biome.Precipitation.SNOW ? Math.min(temperature, SNOWING_TEMPERATURE) : temperature;
    }

    private float seasonalTemperatureAt(BlockPos blockPos) {
        var biome = this.levelOracle.biomeHolder(blockPos).value();
        if (!this.temperatureHookBroken) {
            try {
                return EclipticUtil.getTemperatureFloat(this.levelOracle.level(), biome, blockPos);
            } catch (LinkageError e) {
                this.temperatureHookBroken = true;
                Library.LOGGER.error(e, "Ecliptic Seasons' temperature isn't there in this version: temperatures won't follow the season");
            }
        }
        return biome.getTemperature(blockPos, this.levelOracle.seaLevel());
    }

    /**
     * What may fall at the position in its biome's season, whether or not it is raining now: none (a dry season, or a
     * biome without precipitation), rain, or snow by the solar term
     */
    @Override
    protected Biome.Precipitation precipitationAt(BlockPos blockPos) {
        return api().getPrecipitationAt(this.levelOracle.level(), blockPos);
    }

    @Override
    protected boolean precipitates(BlockPos blockPos) {
        return this.precipitationAt(blockPos) != Biome.Precipitation.NONE;
    }

    @Override
    public boolean snowsAt(BlockPos blockPos, float temperature) {
        return this.getPrecipitationAt(blockPos) == Biome.Precipitation.SNOW;
    }
}

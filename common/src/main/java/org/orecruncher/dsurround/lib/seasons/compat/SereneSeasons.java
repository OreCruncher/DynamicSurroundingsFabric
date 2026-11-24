// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.seasons.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.eventing.IClientTickStart;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.function.CachingSupplier;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.season.SeasonHooks;

import java.util.Locale;
import java.util.Optional;

public class SereneSeasons extends AbstractSeasonProvider {

    private final CachingSupplier<ISeasonState> seasonStateCache;

    // Cache for previously computed data
    private Season.SubSeason subSeason;
    private Season.TropicalSeason tropicalSeason;
    private Component computed;
    // Set once SeasonHooks.getBiomeTemperature is found missing (see temperatureAt)
    private volatile boolean temperatureHookBroken;

    public SereneSeasons(ILevelOracle levelOracle) {
        super("Serene Seasons", levelOracle);
        this.seasonStateCache = CachingSupplier.from(() -> SeasonHelper.getSeasonState(this.level()));
        IClientTickStart.EVENT.register(ignored -> this.seasonStateCache.clear());
    }

    @Override
    public Optional<Component> getCurrentSeason() {
        var helper = this.seasonStateCache.get();
        var subSeason = helper.getSubSeason();
        return Optional.of(Component.literal(subSeason.toString()));
    }

    @Override
    public Optional<Component> getCurrentSeasonTranslated() {
        var helper = this.seasonStateCache.get();
        if (this.subSeason != helper.getSubSeason() || this.tropicalSeason != helper.getTropicalSeason()) {
            var subSeasonKey = "desc.sereneseasons." + helper.getSeason().toString().toLowerCase(Locale.ROOT);
            var tropicalSeasonKey = "desc.sereneseasons." + helper.getTropicalSeason().toString().toLowerCase(Locale.ROOT);
            var subSeason = Component.translatable(subSeasonKey);
            var tropicalSeason = Component.translatable(tropicalSeasonKey);
            this.computed = Component.translatable("%s (%s)", subSeason, tropicalSeason);
            this.subSeason = helper.getSubSeason();
            this.tropicalSeason = helper.getTropicalSeason();
        }

        return Optional.of(this.computed);
    }

    public boolean isSpring() {
        var helper = this.seasonStateCache.get();
        return helper.getSeason() == Season.SPRING;
    }

    public  boolean isSummer() {
        var helper = this.seasonStateCache.get();
        return helper.getSeason() == Season.SUMMER;
    }

    public  boolean isAutumn() {
        var helper = this.seasonStateCache.get();
        return helper.getSeason() == Season.AUTUMN;
    }

    public  boolean isWinter() {
        var helper = this.seasonStateCache.get();
        return helper.getSeason() == Season.WINTER;
    }

    public boolean isEarly() {
        var currentSubSeason = this.seasonStateCache.get().getSubSeason();
        return Season.SubSeason.EARLY_AUTUMN == currentSubSeason || Season.SubSeason.EARLY_SPRING == currentSubSeason || Season.SubSeason.EARLY_SUMMER == currentSubSeason || Season.SubSeason.EARLY_WINTER == currentSubSeason;
    }

    public boolean isMiddle() {
        var currentSubSeason = this.seasonStateCache.get().getSubSeason();
        return Season.SubSeason.MID_AUTUMN == currentSubSeason || Season.SubSeason.MID_SPRING == currentSubSeason ||  Season.SubSeason.MID_SUMMER == currentSubSeason || Season.SubSeason.MID_WINTER == currentSubSeason;
    }

    public boolean isLate() {
        var currentSubSeason = this.seasonStateCache.get().getSubSeason();
        return Season.SubSeason.LATE_AUTUMN == currentSubSeason || Season.SubSeason.LATE_SPRING == currentSubSeason ||  Season.SubSeason.LATE_SUMMER == currentSubSeason || Season.SubSeason.LATE_WINTER == currentSubSeason;
    }

    @Override
    protected Biome.Precipitation precipitationAt(BlockPos blockPos) {
        // Serene Seasons answers vanilla's own question on the client (its mixin on Biome.getPrecipitationAt), so the
        // biome is asked directly: not through the oracle, which would time it a second time
        var biome = this.levelOracle.biomeHolder(blockPos);
        return biome.value().getPrecipitationAt(blockPos, this.levelOracle.seaLevel());
    }

    /**
     * The one question still answered past Serene Seasons' API: how far the season moves a biome's temperature is in
     * its config ({@code biomeTempAdjustment} a sub-season), which the API doesn't give. Its hook isn't API, so a
     * Serene Seasons that renamed or moved it would crash every caller (frost, breath, rain or snow); instead that is
     * logged once and the biome's own temperature used from then on, without the season.
     */
    @Override
    protected float temperatureAt(BlockPos blockPos) {
        var biome = this.levelOracle.biomeHolder(blockPos);
        if (!this.temperatureHookBroken) {
            try {
                return SeasonHooks.getBiomeTemperature(this.levelOracle.level(), biome, blockPos, this.levelOracle.seaLevel());
            } catch (LinkageError e) {
                this.temperatureHookBroken = true;
                Library.LOGGER.error(e, "Serene Seasons' temperature hook isn't there in this version: temperatures won't follow the season");
            }
        }
        return biome.value().getTemperature(blockPos, this.levelOracle.seaLevel());
    }

    @Override
    protected boolean precipitates(BlockPos blockPos) {
        // A tropical biome's dry and wet seasons decide in their middle, rather than the biome's own setting, as
        // Serene Seasons decides it
        var biome = this.levelOracle.biomeHolder(blockPos);
        if (SeasonHelper.usesTropicalSeasons(biome)) {
            switch (this.seasonStateCache.get().getTropicalSeason()) {
                case MID_DRY -> {
                    return false;
                }
                case MID_WET -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return biome.value().hasPrecipitation();
    }
}

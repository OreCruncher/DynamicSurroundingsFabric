package org.orecruncher.dsurround.lib.seasons.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.lib.CachingSupplier;
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

    public SereneSeasons(ILevelOracle levelOracle) {
        super("Serene Seasons", levelOracle);
        this.seasonStateCache = CachingSupplier.from(() -> SeasonHelper.getSeasonState(this.level()));
        ClientState.CLIENT_TICK_START_EVENT.register(ignored -> this.seasonStateCache.clear());
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
    public Biome.Precipitation getPrecipitationAt(BlockPos blockPos) {
        var level = this.levelOracle.level();
        var biome = this.levelOracle.biomeHolder(blockPos);
        return SeasonHooks.getPrecipitationAtSeasonal(level, biome, blockPos, this.levelOracle.seaLevel());
    }

    @Override
    public float getTemperatureAt(BlockPos blockPos) {
        var biome = this.levelOracle.biomeHolder(blockPos);
        var subSeason = this.seasonStateCache.get().getSubSeason();
        return SeasonHooks.getBiomeTemperatureInSeason(subSeason, biome, blockPos, this.levelOracle.seaLevel());
    }
}

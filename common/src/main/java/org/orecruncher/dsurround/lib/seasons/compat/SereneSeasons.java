package org.orecruncher.dsurround.lib.seasons.compat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.libraries.IDimensionInformation;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.season.SeasonHooks;

import java.util.Locale;
import java.util.Optional;

public class SereneSeasons extends AbstractSeasonProvider {

    // Cache for previously computed data
    private Season.SubSeason subSeason;
    private Season.TropicalSeason tropicalSeason;
    private Component computed;

    private final IDimensionInformation dimensionInformation;

    public SereneSeasons(IDimensionInformation dimensionInformation) {
        super("Serene Seasons");
        this.dimensionInformation = dimensionInformation;
    }

    @Override
    public Optional<Component> getCurrentSeason() {
        var helper = SeasonHelper.getSeasonState(this.level());
        var subSeason = helper.getSubSeason();
        return Optional.of(Component.literal(subSeason.toString()));
    }

    @Override
    public Optional<Component> getCurrentSeasonTranslated() {
        var helper = SeasonHelper.getSeasonState(this.level());
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
        var helper = SeasonHelper.getSeasonState(this.level());
        return helper.getSeason() == Season.SPRING;
    }

    public  boolean isSummer() {
        var helper = SeasonHelper.getSeasonState(this.level());
        return helper.getSeason() == Season.SUMMER;
    }

    public  boolean isAutumn() {
        var helper = SeasonHelper.getSeasonState(this.level());
        return helper.getSeason() == Season.AUTUMN;
    }

    public  boolean isWinter() {
        var helper = SeasonHelper.getSeasonState(this.level());
        return helper.getSeason() == Season.WINTER;
    }

    @Override
    public Biome.Precipitation getPrecipitationAt(BlockPos blockPos) {
        var level = this.level();
        var biome = level.getBiome(blockPos);
        return SeasonHooks.getPrecipitationAtSeasonal(level, biome, blockPos);
    }

    @Override
    public float getTemperature(BlockPos blockPos) {
        var level = this.level();
        var biome = level.getBiome(blockPos);
        return SeasonHooks.getBiomeTemperature(level, biome, blockPos);
    }

    @Override
    public ClientLevel level() {
        return this.dimensionInformation.level();
    }
}

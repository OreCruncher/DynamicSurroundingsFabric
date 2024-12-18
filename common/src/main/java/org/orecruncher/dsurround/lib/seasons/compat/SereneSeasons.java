package org.orecruncher.dsurround.lib.seasons.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
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

    public SereneSeasons() {
        super("Serene Seasons");
    }

    @Override
    public Optional<Component> getCurrentSeason(Level world) {
        var helper = SeasonHelper.getSeasonState(world);
        var subSeason = helper.getSubSeason();
        return Optional.of(Component.literal(subSeason.toString()));
    }

    @Override
    public Optional<Component> getCurrentSeasonTranslated(Level world) {
        var helper = SeasonHelper.getSeasonState(world);
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

    public boolean isSpring(Level world) {
        var helper = SeasonHelper.getSeasonState(world);
        return helper.getSeason() == Season.SPRING;
    }

    public  boolean isSummer(Level world) {
        var helper = SeasonHelper.getSeasonState(world);
        return helper.getSeason() == Season.SUMMER;
    }

    public  boolean isAutumn(Level world) {
        var helper = SeasonHelper.getSeasonState(world);
        return helper.getSeason() == Season.AUTUMN;
    }

    public  boolean isWinter(Level world) {
        var helper = SeasonHelper.getSeasonState(world);
        return helper.getSeason() == Season.WINTER;
    }

    @Override
    public Biome.Precipitation getPrecipitationAt(Level world, BlockPos blockPos) {
        var biome = world.getBiome(blockPos);
        return SeasonHooks.getPrecipitationAtSeasonal(world, biome, blockPos);
    }

    @Override
    public float getTemperature(Level world, BlockPos blockPos) {
        var biome = world.getBiome(blockPos);
        return SeasonHooks.getBiomeTemperature(world, biome, blockPos);
    }
}

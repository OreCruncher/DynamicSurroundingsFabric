package org.orecruncher.dsurround.lib.seasons.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.libraries.IReloadEvent;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.season.SeasonHooks;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class SereneSeasons extends AbstractSeasonProvider {

    private final Map<Season.SubSeason, String> subSeasonStringMap = new EnumMap<>(Season.SubSeason.class);
    private final Map<Season.TropicalSeason, String> tropicalSeasonMap = new EnumMap<>(Season.TropicalSeason.class);

    public SereneSeasons() {
        super("Serene Seasons");
    }

    @Override
    public Optional<String> getCurrentSeason(Level world) {
        var helper = SeasonHelper.getSeasonState(world);
        var subSeason = helper.getSubSeason();
        return Optional.of(subSeason.toString());
    }

    @Override
    public Optional<String> getCurrentSeasonTranslated(Level world) {
        var helper = SeasonHelper.getSeasonState(world);
        var subSeason = this.subSeasonStringMap.get(helper.getSubSeason());
        var tropicalSeason = this.tropicalSeasonMap.get(helper.getTropicalSeason());
        var result = "%s (%s)".formatted(subSeason, tropicalSeason);
        return Optional.of(result);
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

    @Override
    protected void reloadResources(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {
        if (scope == IReloadEvent.Scope.TAGS)
            return;
        for (var subSeason : Season.SubSeason.values())
            this.subSeasonStringMap.put(subSeason, Language.getInstance().getOrDefault("desc.sereneseasons." + subSeason.toString().toLowerCase()));
        for (var tropicalSeason : Season.TropicalSeason.values())
            this.tropicalSeasonMap.put(tropicalSeason, Language.getInstance().getOrDefault("desc.sereneseasons." + tropicalSeason.toString().toLowerCase()));
    }
}

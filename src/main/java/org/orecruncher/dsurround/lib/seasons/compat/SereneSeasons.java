package org.orecruncher.dsurround.lib.seasons.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.season.SeasonHooks;

import java.util.Optional;

public class SereneSeasons implements ISeasonalInformation {
    @Override
    public String getProviderName() {
        return "Serene Seasons";
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
        var subSeason = helper.getSubSeason();
        var tropicalSeason = helper.getTropicalSeason();
        var seasonLangId = "desc.sereneseasons." + subSeason.toString().toLowerCase();
        var tropicalSeasonLangId = "desc.sereneseasons." + tropicalSeason.toString().toLowerCase();
        var result = "%s (%s)".formatted(Language.getInstance().getOrDefault(seasonLangId), Language.getInstance().getOrDefault(tropicalSeasonLangId));
        return Optional.of(result);
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

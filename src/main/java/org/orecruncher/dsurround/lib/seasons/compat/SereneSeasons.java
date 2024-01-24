package org.orecruncher.dsurround.lib.seasons.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

import java.util.Optional;

public class SereneSeasons implements ISeasonalInformation {
    @Override
    public String getProviderName() {
        return "Serene Seasons";
    }

    @Override
    public Optional<String> getCurrentSeason() {
        return Optional.empty();
    }

    @Override
    public float getTemperature(Level world, BlockPos blockPos) {
        return 0;
    }
}

package org.orecruncher.dsurround.lib.seasons.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.mixinutils.IBiomeExtended;

import java.util.Optional;

public class VanillaSeasons implements ISeasonalInformation {

    @Override
    public String getProviderName() {
        return "Vanilla";
    }

    @Override
    public Optional<String> getCurrentSeason() {
        // Vanilla doesn't have seasons, so...
        return Optional.empty();
    }

    @Override
    public float getTemperature(Level world, BlockPos blockPos) {
        var biome = world.getBiome(blockPos).value();
        return ((IBiomeExtended)(Object)biome).dsurround_getTemperature(blockPos);
    }
}

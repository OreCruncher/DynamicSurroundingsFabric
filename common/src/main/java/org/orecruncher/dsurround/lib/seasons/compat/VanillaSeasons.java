package org.orecruncher.dsurround.lib.seasons.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.orecruncher.dsurround.mixinutils.IBiomeExtended;

import java.util.Optional;

public class VanillaSeasons extends AbstractSeasonProvider {

    public VanillaSeasons() {
        super("Vanilla");
    }

    @Override
    public Optional<Component> getCurrentSeason(Level world) {
        return Optional.of(Component.translatable("dsurround.text.seasons.spring"));
    }

    @Override
    public Optional<Component> getCurrentSeasonTranslated(Level world) {
        return getCurrentSeason(world);
    }

    @Override
    public float getTemperature(Level world, BlockPos blockPos, int seaLevel) {
        var biome = world.getBiome(blockPos).value();
        return ((IBiomeExtended)(Object)biome).dsurround_getTemperature(blockPos, seaLevel);
    }
}

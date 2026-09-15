package org.orecruncher.dsurround.lib.seasons.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Optional;

public class VanillaSeasons extends AbstractSeasonProvider {

    public VanillaSeasons() {
        super("Vanilla");
    }

    @Override
    public Optional<Component> getCurrentSeason() {
        return Optional.of(Component.translatable("dsurround.text.seasons.spring"));
    }

    @Override
    public Optional<Component> getCurrentSeasonTranslated() {
        return this.getCurrentSeason();
    }

    @Override
    public float getTemperature(BlockPos blockPos) {
        var biome = this.level().getBiome(blockPos).value();
        var info = BIOME_LIBRARY.getBiomeInfo(biome);
        return info.getTemperature(blockPos);
    }
}

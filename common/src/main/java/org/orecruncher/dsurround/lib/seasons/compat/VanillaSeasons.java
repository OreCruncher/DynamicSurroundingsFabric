package org.orecruncher.dsurround.lib.seasons.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;

import java.util.Optional;

public class VanillaSeasons extends AbstractSeasonProvider {

    public VanillaSeasons(ILevelOracle levelOracle) {
        super("Vanilla", levelOracle);
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
    public boolean isSpring() {
        return true;
    }

    @Override
    public boolean isSummer() {
        return false;
    }

    @Override
    public boolean isAutumn() {
        return false;
    }

    @Override
    public boolean isWinter() {
        return false;
    }

    @Override
    public boolean isEarly() {
        return true;
    }

    @Override
    public boolean isMiddle() {
        return true;
    }

    @Override
    public boolean isLate() {
        return true;
    }

    @Override
    public float getTemperatureAt(BlockPos blockPos) {
        return this.levelOracle.temperatureAt(blockPos);
    }

    @Override
    public Biome.Precipitation getPrecipitationAt(BlockPos blockPos) {
        return this.levelOracle.precipitationAt(blockPos);
    }
}

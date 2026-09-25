package org.orecruncher.dsurround.lib.seasons.compat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;

import java.util.Optional;

public abstract class AbstractSeasonProvider implements ISeasonalInformation {

    private final String providerName;
    protected final ILevelOracle levelOracle;

    protected AbstractSeasonProvider(String providerName, ILevelOracle levelOracle) {
        this.providerName = providerName;
        this.levelOracle = levelOracle;
    }

    @Override
    public String getProviderName() {
        return this.providerName;
    }

    @Override
    public abstract Optional<Component> getCurrentSeason();

    @Override
    public abstract Optional<Component> getCurrentSeasonTranslated();

    @Override
    public abstract boolean isSpring();

    @Override
    public abstract boolean isSummer();

    @Override
    public abstract boolean isAutumn();

    @Override
    public abstract boolean isWinter();

    @Override
    public abstract boolean isEarly();

    @Override
    public abstract boolean isMiddle();

    @Override
    public abstract boolean isLate();

    @Override
    public abstract float getTemperatureAt(BlockPos blockPos);

    @Override
    public boolean isColdTemperature(BlockPos blockPos) {
        return this.getTemperatureAt(blockPos) < 0.2F;
    }

    @Override
    public boolean isSnowTemperature(BlockPos blockPos) {
        return this.getTemperatureAt(blockPos) < 0.15F;
    }

    @Override
    public int getPrecipitationHeight(BlockPos pos) {
        return this.levelOracle.level().getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    @Override
    public abstract Biome.Precipitation getPrecipitationAt(BlockPos blockPos);

    @Override
    public Biome.Precipitation getActivePrecipitationAt(BlockPos pos) {
        if (!this.levelOracle.isRaining()) {
            // Not currently raining
            return Biome.Precipitation.NONE;
        }

        // If the biome has no rain...
        if (this.getPrecipitationAt(pos) == Biome.Precipitation.NONE)
            return Biome.Precipitation.NONE;

        // Is there a block above that is blocking the rainfall?
        var p = this.getPrecipitationHeight(pos);
        if (p > pos.getY()) {
            return Biome.Precipitation.NONE;
        }

        // Use the temperature of the biome to get whether it is raining or snowing
        return this.isSnowTemperature(pos) ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
    }

    @Override
    public ClientLevel level() {
        return this.levelOracle.level();
    }
}

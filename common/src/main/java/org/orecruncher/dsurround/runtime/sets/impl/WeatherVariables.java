package org.orecruncher.dsurround.runtime.sets.impl;

import org.orecruncher.dsurround.config.libraries.IDimensionLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.scripting.IVariableAccess;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.runtime.sets.IWeatherVariables;

public class WeatherVariables extends VariableSet<IWeatherVariables> implements IWeatherVariables {

    private final ISeasonalInformation seasonalInformation;
    private final IDimensionLibrary dimensionLibrary;

    private float temperature;
    private boolean isRaining;
    private boolean isThundering;
    private float rainIntensity;
    private float thunderIntensity;
    private boolean isFrosty;
    private boolean canWaterFreeze;

    public WeatherVariables(ISeasonalInformation seasonalInformation, IDimensionLibrary dimensionLibrary) {
        super("weather");
        this.seasonalInformation = seasonalInformation;
        this.dimensionLibrary = dimensionLibrary;
    }

    @Override
    public IWeatherVariables getInterface() {
        return this;
    }

    @Override
    public void update(IVariableAccess variableAccess) {
        if (GameUtils.isInGame()) {
            final var player = GameUtils.getPlayer().orElseThrow();
            final var world = player.level();
            final var seaLevel = this.dimensionLibrary.getData(world).getSeaLevel();
            this.rainIntensity = world.getRainLevel(1F);
            this.thunderIntensity = world.getThunderLevel(1F);
            this.isRaining = world.isRaining();
            this.isThundering = world.isThundering();
            this.temperature = this.seasonalInformation.getTemperature(world, player.blockPosition(), seaLevel);
            this.isFrosty = this.seasonalInformation.isColdTemperature(world, player.blockPosition(), seaLevel);
            this.canWaterFreeze = this.seasonalInformation.isSnowTemperature(world, player.blockPosition(), seaLevel);
        } else {
            this.rainIntensity = 0F;
            this.thunderIntensity = 0F;
            this.isRaining = false;
            this.isThundering = false;
            this.temperature = 0;
            this.isFrosty = false;
            this.canWaterFreeze = false;
        }
    }

    @Override
    public boolean isRaining() {
        return this.isRaining;
    }

    @Override
    public boolean isThundering() {
        return this.isThundering;
    }

    @Override
    public float getRainIntensity() {
        return this.rainIntensity;
    }

    @Override
    public float getThunderIntensity() {
        return this.thunderIntensity;
    }

    @Override
    public float getTemperature() {
        return this.temperature;
    }

    @Override
    public boolean isFrosty() {
        return this.isFrosty;
    }

    @Override
    public boolean canWaterFreeze() {
        return this.canWaterFreeze;
    }
}
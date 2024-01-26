package org.orecruncher.dsurround.runtime.sets;

import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.scripting.IVariableAccess;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

public class WeatherVariables extends VariableSet<IWeatherVariables> implements IWeatherVariables {

    private static final ISeasonalInformation SEASONAL_INFORMATION = ContainerManager.resolve(ISeasonalInformation.class);

    private String season;
    private float temperature;
    private boolean isRaining;
    private boolean isThundering;
    private float rainIntensity;
    private float thunderIntensity;
    private boolean isFrosty;
    private boolean canWaterFreeze;

    public WeatherVariables() {
        super("weather");
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
            this.rainIntensity = world.getRainLevel(1F);
            this.thunderIntensity = world.getThunderLevel(1F);
            this.isRaining = world.isRaining();
            this.isThundering = world.isThundering();
            this.temperature = SEASONAL_INFORMATION.getTemperature(world, player.blockPosition());
            this.season = SEASONAL_INFORMATION.getCurrentSeason(world).orElse("NONE");
            this.isFrosty = SEASONAL_INFORMATION.isColdTemperature(world, player.blockPosition());
            this.canWaterFreeze = SEASONAL_INFORMATION.isSnowTemperature(world, player.blockPosition());
        } else {
            this.rainIntensity = 0F;
            this.thunderIntensity = 0F;
            this.isRaining = false;
            this.isThundering = false;
            this.temperature = 0;
            this.season = "NONE";
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

    @Override
    public String getSeason() {
        return this.season;
    }
}
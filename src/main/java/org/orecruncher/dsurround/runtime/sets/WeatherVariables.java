package org.orecruncher.dsurround.runtime.sets;

import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.IVariableAccess;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.world.WorldUtils;

public class WeatherVariables extends VariableSet<IWeatherVariables> implements IWeatherVariables {

    private float temperature;
    private boolean isRaining;
    private boolean isThundering;
    private float rainIntensity;
    private float thunderIndensity;

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
            this.thunderIndensity = world.getThunderLevel(1F);
            this.isRaining = world.isRaining();
            this.isThundering = world.isThundering();
            this.temperature = WorldUtils.getTemperatureAt(world, player.blockPosition());
        } else {
            this.rainIntensity = 0F;
            this.thunderIndensity = 0F;
            this.isRaining = false;
            this.isThundering = false;
            this.temperature = 0;
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
        return this.thunderIndensity;
    }

    @Override
    public float getTemperature() {
        return this.temperature;
    }
}
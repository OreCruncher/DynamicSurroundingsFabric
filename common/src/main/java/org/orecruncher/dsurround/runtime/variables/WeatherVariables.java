package org.orecruncher.dsurround.runtime.variables;

import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

public class WeatherVariables extends VariableSet {

    private final ISeasonalInformation seasonalInformation;

    private float temperature;
    private boolean isRaining;
    private boolean isThundering;
    private float rainIntensity;
    private float thunderIntensity;
    private boolean isFrosty;
    private boolean canWaterFreeze;

    public WeatherVariables(ISeasonalInformation seasonalInformation) {
        super("weather");
        this.seasonalInformation = seasonalInformation;
    }

    @Override
    public void tick() {
        if (GameUtils.isInGame()) {
            final var player = GameUtils.getPlayer().orElseThrow();
            final var world = player.level();
            this.rainIntensity = world.getRainLevel(1F);
            this.thunderIntensity = world.getThunderLevel(1F);
            this.isRaining = world.isRaining();
            this.isThundering = world.isThundering();
            this.temperature = this.seasonalInformation.getTemperature(player.blockPosition());
            this.isFrosty = this.seasonalInformation.isColdTemperature(player.blockPosition());
            this.canWaterFreeze = this.seasonalInformation.isSnowTemperature(player.blockPosition());
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
    public void configure(IConfigureDefinition config) {
        config.defineFunction(id("isRaining"), l -> this.isRaining);
        config.defineFunction(id("isNotRaining"), l -> !this.isRaining);
        config.defineFunction(id("isThundering"), l -> this.isThundering);
        config.defineFunction(id("getRainIntensity"), l -> this.rainIntensity);
        config.defineFunction(id("getThunderIntensity"), l -> this.thunderIntensity);
        config.defineFunction(id("getTemperature"), l -> this.temperature);
        config.defineFunction(id("isFrosty"), l -> this.isFrosty);
        config.defineFunction(id("canWaterFreeze"), l -> this.canWaterFreeze);
    }
}
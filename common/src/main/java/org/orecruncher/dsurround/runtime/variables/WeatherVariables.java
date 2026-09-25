package org.orecruncher.dsurround.runtime.variables;

import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;

public final class WeatherVariables extends VariableSet {

    private final ILevelOracle levelOracle;
    private final ISeasonalInformation seasonalInformation;

    private float temperature;
    private boolean isRaining;
    private boolean isThundering;
    private float rainIntensity;
    private float thunderIntensity;
    private boolean isFrosty;
    private boolean canWaterFreeze;

    public WeatherVariables(ILevelOracle levelOracle, ISeasonalInformation seasonalInformation) {
        super("weather");
        this.levelOracle = levelOracle;
        this.seasonalInformation = seasonalInformation;
    }

    @Override
    public void tick() {
        if (GameUtils.isInGame()) {
            final var player = GameUtils.getPlayer().orElseThrow();
            this.rainIntensity = this.levelOracle.getRainLevel();
            this.thunderIntensity = this.levelOracle.getThunderLevel();
            this.isRaining = this.levelOracle.isRaining();
            this.isThundering = this.levelOracle.isThundering();
            this.temperature = this.seasonalInformation.getTemperatureAt(player.blockPosition());
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
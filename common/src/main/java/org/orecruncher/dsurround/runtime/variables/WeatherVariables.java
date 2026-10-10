package org.orecruncher.dsurround.runtime.variables;

import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;

public final class WeatherVariables extends VariableSet {

    private final ILevelOracle levelOracle;
    private final ISeasonalInformation seasonalInformation;

    // Numeric values are stored boxed when updated each tick, so that reading them from scripts does not allocate
    private Float temperature = 0F;
    private boolean isRaining;
    private boolean isThundering;
    private Float rainIntensity = 0F;
    private Float thunderIntensity = 0F;
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
            this.temperature = 0F;
            this.isFrosty = false;
            this.canWaterFreeze = false;
        }
    }

    @Override
    public void configure(IConfigureDefinition config) {
        config.property(id("isRaining"), () -> this.isRaining);
        config.property(id("isNotRaining"), () -> !this.isRaining);
        config.property(id("isThundering"), () -> this.isThundering);
        config.property(id("getRainIntensity"), () -> this.rainIntensity);
        config.property(id("getThunderIntensity"), () -> this.thunderIntensity);
        config.property(id("getTemperature"), () -> this.temperature);
        config.property(id("isFrosty"), () -> this.isFrosty);
        config.property(id("canWaterFreeze"), () -> this.canWaterFreeze);
    }
}
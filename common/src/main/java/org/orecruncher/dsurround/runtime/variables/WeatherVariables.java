package org.orecruncher.dsurround.runtime.variables;

import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

public final class WeatherVariables extends VariableSet {

    private final ISeasonalInformation seasonalInformation;

    // Numeric values are stored boxed when updated each tick, so that reading them from scripts does not allocate
    private Float temperature = 0F;
    private boolean isRaining;
    private boolean isThundering;
    private Float rainIntensity = 0F;
    private Float thunderIntensity = 0F;
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
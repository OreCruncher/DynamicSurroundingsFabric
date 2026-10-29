package org.orecruncher.dsurround.runtime.variables;

import org.orecruncher.dsurround.runtime.weather.Wind;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.math.Hysteresis;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.runtime.weather.WindService;

public final class WeatherVariables extends VariableSet {

    private final ILevelOracle levelOracle;
    private final ISeasonalInformation seasonalInformation;
    private final IWeatherSimulation weatherSimulation;

    // Numeric values are stored boxed when updated each tick, so that reading them from scripts does not allocate
    private Float temperature = 0F;
    private boolean isRaining;
    private boolean isThundering;
    private Float rainIntensity = 0F;
    private Float thunderIntensity = 0F;
    private boolean isFrosty;
    private boolean canWaterFreeze;

    /**
     * The wind's speed, as a percent of a full storm's, from which it counts as windy
     */
    static final float WINDY_FROM = 50F;
    /**
     * The wind counts as a storm's from 70 percent of a full storm's, and stops counting only below 55: a sound
     * switched by it doesn't start and stop as the wind wavers around one line
     */
    private static final float STORM_WIND_FROM = 70F;
    private static final float STORM_WIND_UNTIL = 55F;

    private final WindService windService;
    private float windSpeed;
    private boolean isGusting;
    private final Hysteresis stormWind = new Hysteresis(STORM_WIND_FROM, STORM_WIND_UNTIL);

    public WeatherVariables(ILevelOracle levelOracle, ISeasonalInformation seasonalInformation, IWeatherSimulation weatherSimulation, WindService windService) {
        super("weather");
        this.levelOracle = levelOracle;
        this.windService = windService;
        this.seasonalInformation = seasonalInformation;
        this.weatherSimulation = weatherSimulation;
    }

    @Override
    public void tick() {
        if (GameUtils.isInGame()) {
            final var player = GameUtils.getPlayer().orElseThrow();
            // The storm's strength. Whether it is raining at all is the world's: a light storm is still rain.
            this.rainIntensity = this.weatherSimulation.rainLevel(this.levelOracle.getRainLevel());
            this.thunderIntensity = this.levelOracle.getThunderLevel();
            this.isRaining = this.levelOracle.isRaining();
            this.isThundering = this.levelOracle.isThundering();
            this.temperature = this.seasonalInformation.getTemperatureAt(player.blockPosition());
            this.isFrosty = this.seasonalInformation.isColdTemperature(player.blockPosition());
            this.canWaterFreeze = this.seasonalInformation.isSnowTemperature(player.blockPosition());
            // The steady wind, as a percent of a full storm's, as /dsweather wind shows it. Without gusts: they come and
            // go in seconds, and would flip anything switched by the speed
            this.windSpeed = Wind.percentOfFullStorm(this.windService.steadySpeed());
            this.stormWind.update(this.windSpeed);
            this.isGusting = !this.windService.isStill() && this.windService.wind().isGusting();
        } else {
            this.rainIntensity = 0F;
            this.thunderIntensity = 0F;
            this.isRaining = false;
            this.isThundering = false;
            this.temperature = 0F;
            this.isFrosty = false;
            this.canWaterFreeze = false;
            this.windSpeed = 0F;
            this.isGusting = false;
            this.stormWind.reset();
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
        config.property(id("getWindSpeed"), () -> this.windSpeed);
        config.property(id("isWindy"), () -> this.windSpeed >= WINDY_FROM);
        config.property(id("isGusting"), () -> this.isGusting);
        config.property(id("isStormWind"), this.stormWind::isOn);
    }
}
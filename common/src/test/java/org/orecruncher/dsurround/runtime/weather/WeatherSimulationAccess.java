package org.orecruncher.dsurround.runtime.weather;

import org.orecruncher.dsurround.Configuration;

/**
 * Lets tests outside this package drive a weather simulation tick by tick, without a world
 */
public final class WeatherSimulationAccess {

    private final WeatherSimulation simulation;

    public WeatherSimulationAccess(Configuration.WeatherOptions options) {
        this.simulation = new WeatherSimulation(options);
    }

    public IWeatherSimulation simulation() {
        return this.simulation;
    }

    /**
     * A tick with the game's rain at {@code vanillaLevel}, on the given day, at the start of the game's time
     */
    public void update(float vanillaLevel, long day) {
        this.simulation.update(vanillaLevel, day, 0L);
    }
}

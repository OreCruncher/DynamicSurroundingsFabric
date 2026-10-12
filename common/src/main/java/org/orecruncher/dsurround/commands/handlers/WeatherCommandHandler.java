package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.runtime.weather.Wind;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.processing.GroundFogHandler;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.runtime.weather.WindService;

import java.util.Locale;

/**
 * /dsweather: shows the storm's strength, and sets it, for seeing how storms of a strength look. Strengths are percents
 * of full strength, as the storm strength options are. /dsweather wind does the same for the wind's speed, as a percent
 * of a full storm's wind, and the way it blows.
 */
public class WeatherCommandHandler {

    private static final String KEY = "dsurround.command.dsweather.";

    public static Component status() {
        return status(simulation());
    }

    public static Component strength(int percent) {
        return strength(simulation(), percent);
    }

    public static Component clear() {
        return clear(simulation());
    }

    public static Component windStatus() {
        return windStatus(wind());
    }

    public static Component windSpeed(int percent) {
        return windSpeed(wind(), percent);
    }

    public static Component windDirection(float bearing) {
        return windDirection(wind(), bearing);
    }

    public static Component windClear() {
        return windClear(wind());
    }

    public static Component groundFog(int percent) {
        return groundFog(ContainerManager.resolve(GroundFogHandler.class), percent);
    }

    public static Component groundFogStatus() {
        return groundFogStatus(ContainerManager.resolve(GroundFogHandler.class));
    }

    static Component groundFogStatus(GroundFogHandler handler) {
        return handler.forced()
                .map(thickness -> Component.translatable(KEY + "groundfog.status.set", percent(thickness)))
                .orElseGet(() -> Component.translatable(KEY + "groundfog.status"));
    }

    public static Component groundFogClear() {
        return groundFogClear(ContainerManager.resolve(GroundFogHandler.class));
    }

    static Component groundFog(GroundFogHandler handler, int percent) {
        handler.setForced(percent / 100F);
        return Component.translatable(KEY + "groundfog.success", percent);
    }

    static Component groundFogClear(GroundFogHandler handler) {
        var wasSet = handler.forced().isPresent();
        handler.clearForced();
        return Component.translatable(KEY + (wasSet ? "groundfog.clear.success" : "groundfog.clear.notset"));
    }

    private static IWeatherSimulation simulation() {
        return ContainerManager.resolve(IWeatherSimulation.class);
    }

    private static Wind wind() {
        return ContainerManager.resolve(WindService.class).wind();
    }

    static Component windStatus(Wind wind) {
        var bearing = Math.round(wind.bearing()) % 360;
        return Component.translatable(KEY + (wind.isSet() ? "wind.status.set" : "wind.status"),
                Wind.percentOfFullStorm(wind.speed()), Wind.compassPoint(wind.bearing()), bearing);
    }

    static Component windSpeed(Wind wind, int percent) {
        wind.setSpeed(Wind.speedOfPercent(percent));
        return Component.translatable(KEY + "wind.speed.success", percent);
    }

    static Component windDirection(Wind wind, float bearing) {
        wind.setBearing(bearing);
        return Component.translatable(KEY + "wind.direction.success", Wind.compassPoint(bearing), Math.round(bearing) % 360);
    }

    static Component windClear(Wind wind) {
        var wasSet = wind.isSet();
        wind.clearSet();
        return Component.translatable(KEY + (wasSet ? "wind.clear.success" : "wind.clear.notset"));
    }

    static Component status(IWeatherSimulation simulation) {
        var set = simulation.strengthSet();
        if (simulation.vanillaLevel() <= 0F) {
            return set.map(strength -> Component.translatable(KEY + "status.none.set", percent(strength)))
                    .orElseGet(() -> Component.translatable(KEY + "status.none"));
        }
        var band = simulation.band().name().toLowerCase(Locale.ROOT);
        var vanilla = percent(simulation.vanillaLevel());
        if (set.isPresent())
            return Component.translatable(KEY + "status.set", band, percent(simulation.intensity()), percent(set.get()), vanilla);
        if (!simulation.isSimulating())
            return Component.translatable(KEY + "status.disabled", vanilla);
        return Component.translatable(KEY + "status", band, percent(simulation.intensity()), percent(simulation.maxIntensity()), vanilla);
    }

    static Component strength(IWeatherSimulation simulation, int percent) {
        simulation.setStrength(percent / 100F);
        var raining = simulation.vanillaLevel() > 0F;
        return Component.translatable(KEY + (raining ? "strength.success" : "strength.notraining"), percent);
    }

    static Component clear(IWeatherSimulation simulation) {
        var wasSet = simulation.strengthSet().isPresent();
        simulation.clearStrength();
        return Component.translatable(KEY + (wasSet ? "clear.success" : "clear.notset"));
    }

    private static int percent(float level) {
        return Math.round(level * 100F);
    }
}

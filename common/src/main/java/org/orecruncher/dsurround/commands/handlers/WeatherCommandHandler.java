package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;

import java.util.Locale;

/**
 * /dsweather: shows the storm's strength, and sets it, for seeing how storms of a strength look. Strengths are percents
 * of full strength, as the storm strength options are.
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

    private static IWeatherSimulation simulation() {
        return ContainerManager.resolve(IWeatherSimulation.class);
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

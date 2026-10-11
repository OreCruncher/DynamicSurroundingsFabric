package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.precipitation.WindAccess;
import org.orecruncher.dsurround.effects.precipitation.Wind;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.runtime.weather.WeatherSimulationAccess;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the messages of /dsweather, and what it sets.
 */
public class WeatherCommandHandlerTests {

    private static final String KEY = "dsurround.command.dsweather.";

    private static TranslatableContents contents(Component component) {
        return assertInstanceOf(TranslatableContents.class, component.getContents());
    }

    private static WeatherSimulationAccess simulation(boolean enabled) {
        var options = new Configuration.WeatherOptions();
        options.enableStormSimulation = enabled;
        options.minStormIntensity = 40;
        options.maxStormIntensity = 40;
        options.stormVariation = 0;
        return new WeatherSimulationAccess(options);
    }

    @Test
    void statusWithNoStorm() {
        var sim = simulation(true);
        assertEquals(KEY + "status.none", contents(WeatherCommandHandler.status(sim.simulation())).getKey());

        WeatherCommandHandler.strength(sim.simulation(), 70);
        var set = contents(WeatherCommandHandler.status(sim.simulation()));
        assertEquals(KEY + "status.none.set", set.getKey());
        assertArrayEquals(new Object[]{70}, set.getArgs());
    }

    @Test
    void statusDuringAStorm() {
        var sim = simulation(true);
        sim.update(1F, 3);
        var status = contents(WeatherCommandHandler.status(sim.simulation()));
        assertEquals(KEY + "status", status.getKey());
        // The band, how strong it is now, the most it will be, and the game's own rain, in percent
        assertArrayEquals(new Object[]{"moderate", 40, 40, 100}, status.getArgs());
    }

    @Test
    void statusWithTheSimulationOff() {
        var sim = simulation(false);
        sim.update(0.6F, 3);
        var status = contents(WeatherCommandHandler.status(sim.simulation()));
        assertEquals(KEY + "status.disabled", status.getKey());
        assertArrayEquals(new Object[]{60}, status.getArgs());
    }

    @Test
    void settingTheStrengthTakesEffectAtOnce() {
        var sim = simulation(true);
        sim.update(1F, 3);
        var message = contents(WeatherCommandHandler.strength(sim.simulation(), 90));
        assertEquals(KEY + "strength.success", message.getKey());
        assertArrayEquals(new Object[]{90}, message.getArgs());
        assertEquals(0.9F, sim.simulation().rainLevel(1F), 1e-6F, "without waiting for a tick");

        var status = contents(WeatherCommandHandler.status(sim.simulation()));
        assertEquals(KEY + "status.set", status.getKey());
    }

    @Test
    void settingTheStrengthBetweenStormsSaysWhenItApplies() {
        var sim = simulation(true);
        assertEquals(KEY + "strength.notraining", contents(WeatherCommandHandler.strength(sim.simulation(), 30)).getKey());
    }

    @Test
    void windStatusSaysHowItBlows() {
        var wind = new Wind();
        WindAccess.set(wind, 0F, Wind.speedOfPercent(80));
        var status = contents(WeatherCommandHandler.windStatus(wind));
        assertEquals(KEY + "wind.status", status.getKey());
        // Due east, at 80% of a full storm's wind
        assertArrayEquals(new Object[]{80, "E", 90}, status.getArgs());
    }

    @Test
    void settingTheWindHoldsIt() {
        var wind = new Wind();
        var speed = contents(WeatherCommandHandler.windSpeed(wind, 150));
        assertEquals(KEY + "wind.speed.success", speed.getKey());
        assertEquals(Wind.speedOfPercent(150), wind.speed(), 1e-6F);

        var direction = contents(WeatherCommandHandler.windDirection(wind, Wind.bearingOf("SW")));
        assertEquals(KEY + "wind.direction.success", direction.getKey());
        assertArrayEquals(new Object[]{"SW", 225}, direction.getArgs());
        assertEquals(225F, wind.bearing(), 0.01F);

        assertEquals(KEY + "wind.status.set", contents(WeatherCommandHandler.windStatus(wind)).getKey());
    }

    @Test
    void clearingTheWindLetsItGo() {
        var wind = new Wind();
        assertEquals(KEY + "wind.clear.notset", contents(WeatherCommandHandler.windClear(wind)).getKey());
        WeatherCommandHandler.windSpeed(wind, 50);
        assertEquals(KEY + "wind.clear.success", contents(WeatherCommandHandler.windClear(wind)).getKey());
        assertFalse(wind.isSet());
    }

    @Test
    void clearingGoesBackToSimulatedStorms() {
        var sim = simulation(true);
        sim.update(1F, 3);
        assertEquals(KEY + "clear.notset", contents(WeatherCommandHandler.clear(sim.simulation())).getKey());

        WeatherCommandHandler.strength(sim.simulation(), 90);
        assertEquals(KEY + "clear.success", contents(WeatherCommandHandler.clear(sim.simulation())).getKey());
        assertEquals(0.4F, sim.simulation().rainLevel(1F), 1e-6F, "this storm's own strength, straight away");
        assertTrue(sim.simulation().strengthSet().isEmpty());
    }
}

package org.orecruncher.dsurround.runtime.weather;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Configuration;

import java.util.stream.LongStream;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the storm simulation: picking each storm's strength, and capping the rain level at it.
 */
public class WeatherSimulationTests {

    private static WeatherSimulation simulation(boolean enabled, int min, int max) {
        var options = new Configuration.WeatherOptions();
        options.enableStormSimulation = enabled;
        options.minStormIntensity = min;
        options.maxStormIntensity = max;
        // Held steady at its maximum: the surges are tested on their own
        options.stormVariation = 0;
        return new WeatherSimulation(options);
    }

    @Test
    void bandsCoverTheLevelsUpToTheirOwn() {
        assertEquals(StormIntensity.NONE, StormIntensity.of(0F));
        assertEquals(StormIntensity.CALM, StormIntensity.of(0.01F));
        assertEquals(StormIntensity.CALM, StormIntensity.of(0.125F));
        assertEquals(StormIntensity.LIGHT, StormIntensity.of(0.126F));
        assertEquals(StormIntensity.MODERATE, StormIntensity.of(0.5F));
        assertEquals(StormIntensity.TORRENTIAL, StormIntensity.of(1F));
        assertEquals(StormIntensity.TORRENTIAL, StormIntensity.of(1.5F));
    }

    @Test
    void everyClientPicksTheSameStormForTheSameDay() {
        for (long day = 0; day < 50; day++)
            assertEquals(WeatherSimulation.stormMaximum(day, 10, 100), WeatherSimulation.stormMaximum(day, 10, 100));
    }

    @Test
    void stormsStayInTheConfiguredRangeAndVary() {
        var maximums = LongStream.range(0, 500).mapToObj(day -> WeatherSimulation.stormMaximum(day, 20, 60)).toList();
        for (var max : maximums)
            assertTrue(max >= 0.2F && max <= 0.6F, () -> "out of range: " + max);
        assertTrue(maximums.stream().distinct().count() > 100, "storms should differ from day to day");
        // Strengths near the middle of the range are the most likely
        var middle = maximums.stream().filter(m -> m > 0.3F && m < 0.5F).count();
        assertTrue(middle > maximums.size() / 2, () -> "only " + middle + " near the middle");
    }

    @Test
    void aMaximumBelowTheMinimumIsTheMinimum() {
        assertEquals(0.5F, WeatherSimulation.stormMaximum(3, 50, 20), 1e-6F);
        assertEquals(0.4F, WeatherSimulation.stormMaximum(3, 40, 40), 1e-6F);
    }

    @Test
    void rainBuildsUpToTheStormsMaximumAndStaysThere() {
        var sim = simulation(true, 20, 60);
        var expected = WeatherSimulation.stormMaximum(7, 20, 60);

        sim.update(0.05F, 7, 0L);
        assertEquals(expected, sim.maxIntensity());
        assertEquals(Math.min(0.05F, expected), sim.intensity());

        sim.update(1F, 7, 0L);
        assertEquals(expected, sim.intensity());
        assertEquals(StormIntensity.of(expected), sim.band());
        assertEquals(expected, sim.rainLevel(0.9F));
        assertEquals(0.1F, sim.rainLevel(0.1F));
    }

    @Test
    void aStormKeepsItsStrengthPastMidnight() {
        var sim = simulation(true, 10, 100);
        sim.update(1F, 7, 0L);
        var max = sim.maxIntensity();

        sim.update(1F, 8, 0L);
        assertEquals(max, sim.maxIntensity());
    }

    @Test
    void theNextStormPicksAgain() {
        var sim = simulation(true, 10, 100);
        sim.update(1F, 7, 0L);

        sim.update(0F, 9, 0L);
        assertEquals(0F, sim.maxIntensity());
        assertEquals(0F, sim.intensity());
        assertEquals(StormIntensity.NONE, sim.band());

        sim.update(1F, 9, 0L);
        assertEquals(WeatherSimulation.stormMaximum(9, 10, 100), sim.maxIntensity());
    }

    @Test
    void diagnosticsShowTheWorldsRainLevelToo() {
        var sim = simulation(true, 20, 20);
        assertEquals("Storm: none (vanilla 0.00)", sim.diagnosticText());

        sim.update(0.8F, 7, 0L);
        assertEquals("Storm: LIGHT 0.20/0.20 (vanilla 0.80)", sim.diagnosticText());

        var disabled = simulation(false, 20, 20);
        disabled.update(0.8F, 7, 0L);
        assertEquals("Storm: simulation disabled (vanilla 0.80)", disabled.diagnosticText());
    }

    @Test
    void aSetStrengthAppliesToThisStormAndTheNext() {
        var sim = simulation(true, 20, 20);
        sim.update(1F, 7, 0L);
        sim.setStrength(0.8F);
        assertEquals(0.8F, sim.rainLevel(1F), 1e-6F);
        sim.update(1F, 7, 0L);
        assertEquals(0.8F, sim.maxIntensity(), 1e-6F);
        assertEquals(0.8F, sim.intensity(), 1e-6F);

        // The storm ends and another begins, on another day: still the set strength
        sim.update(0F, 7, 0L);
        sim.update(1F, 9, 0L);
        assertEquals(0.8F, sim.maxIntensity(), 1e-6F);
        assertEquals(Optional.of(0.8F), sim.strengthSet());
    }

    @Test
    void aSetStrengthAppliesWithTheSimulationOff() {
        var sim = simulation(false, 20, 20);
        sim.update(1F, 7, 0L);
        assertEquals(1F, sim.rainLevel(1F), "the game's own strength");
        sim.setStrength(0.3F);
        assertEquals(0.3F, sim.rainLevel(1F), 1e-6F);
        assertFalse(sim.isSimulating());
    }

    @Test
    void aSetStrengthIsKeptBetweenZeroAndFull() {
        var sim = simulation(true, 20, 20);
        sim.setStrength(1.5F);
        assertEquals(Optional.of(1F), sim.strengthSet());
        sim.setStrength(-1F);
        assertEquals(Optional.of(0F), sim.strengthSet());
        sim.update(1F, 7, 0L);
        assertEquals(0F, sim.rainLevel(1F), "no storm to see at nothing");
    }

    @Test
    void diagnosticsShowASetStrength() {
        var sim = simulation(true, 20, 20);
        sim.setStrength(0.5F);
        sim.update(0.8F, 7, 0L);
        assertEquals("Storm: MODERATE 0.50/0.50 (set to 0.50) (vanilla 0.80)", sim.diagnosticText());
    }

    @Test
    void withoutVariationAStormHoldsSteady() {
        for (long t = 0; t < 5000; t += 37)
            assertEquals(1F, WeatherSimulation.surge(t, 0));
    }

    @Test
    void aStormSurgesAndEasesWithinItsVariation() {
        float lowest = 1F, highest = 0F;
        for (long t = 0; t < 100_000; t += 7) {
            float surge = WeatherSimulation.surge(t, 40);
            assertTrue(surge >= 0.6F - 1e-5F && surge <= 1F + 1e-5F, () -> "out of range: " + surge);
            lowest = Math.min(lowest, surge);
            highest = Math.max(highest, surge);
        }
        assertTrue(lowest < 0.7F && highest > 0.9F, "should use most of its range");
    }

    @Test
    void aStormChangesSmoothly() {
        // No jump from one tick to the next, even where one surge's height gives way to the next
        for (long t = 0; t < 20_000; t++) {
            float step = Math.abs(WeatherSimulation.surge(t + 1, 100) - WeatherSimulation.surge(t, 100));
            assertTrue(step < 3F / WeatherSimulation.SURGE_TICKS, () -> "jumped " + step);
        }
    }

    @Test
    void everyPlayerSeesTheSameSurges() {
        for (long t = 0; t < 5000; t += 113)
            assertEquals(WeatherSimulation.surge(t, 40), WeatherSimulation.surge(t, 40));
    }

    @Test
    void theSurgeCapsTheRainBelowTheStormsMaximum() {
        var options = new Configuration.WeatherOptions();
        options.minStormIntensity = 80;
        options.maxStormIntensity = 80;
        options.stormVariation = 50;
        var sim = new WeatherSimulation(options);
        for (long t = 0; t < 20_000; t += 50) {
            sim.update(1F, 3, t);
            assertEquals(0.8F, sim.maxIntensity(), 1e-6F, "the peak stays");
            assertEquals(0.8F * WeatherSimulation.surge(t, 50), sim.intensity(), 1e-5F);
        }
    }

    @Test
    void aSetStrengthDoesNotSurge() {
        var options = new Configuration.WeatherOptions();
        options.stormVariation = 100;
        var sim = new WeatherSimulation(options);
        sim.setStrength(0.6F);
        for (long t = 0; t < 5000; t += 50) {
            sim.update(1F, 3, t);
            assertEquals(0.6F, sim.intensity(), 1e-6F);
        }
    }

    @Test
    void disabledLeavesTheRainAlone() {
        var sim = simulation(false, 10, 20);
        sim.update(1F, 7, 0L);

        assertEquals(1F, sim.rainLevel(1F));
        assertEquals(1F, sim.intensity());
    }
}

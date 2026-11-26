// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.testing;

import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.lib.system.ISystemClock;
import org.orecruncher.dsurround.runtime.ConditionEvaluator;
import org.orecruncher.dsurround.runtime.PlatformFunctions;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;
import org.orecruncher.dsurround.runtime.variables.BiomeVariables;
import org.orecruncher.dsurround.runtime.variables.DimensionVariables;
import org.orecruncher.dsurround.runtime.variables.DiurnalVariables;
import org.orecruncher.dsurround.runtime.variables.EnvironmentState;
import org.orecruncher.dsurround.runtime.variables.GlobalVariables;
import org.orecruncher.dsurround.runtime.variables.PlayerVariables;
import org.orecruncher.dsurround.runtime.variables.SeasonVariables;
import org.orecruncher.dsurround.runtime.variables.WeatherVariables;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.runtime.weather.RainbowState;

import java.util.Map;

/**
 * The game's script engines as it builds them, for tests: one place to change when a variable set is added or takes
 * something new
 */
public final class Evaluators {

    private Evaluators() {
    }

    /**
     * The conditions engine, with every variable set and function the scripts can name. Their values are never updated
     * (nothing ticks them), and what they would read the world through is never asked.
     *
     * @param clock the platform functions' clock (the time of day and date scripts see)
     */
    public static ConditionEvaluator conditions(IModLog log, ISystemClock clock) {
        return conditions(log, clock, new Configuration());
    }

    /**
     * @param config what the global variables read
     */
    public static ConditionEvaluator conditions(IModLog log, ISystemClock clock, Configuration config) {
        var oracle = Fakes.of(ILevelOracle.class, Map.of());
        var seasons = Fakes.of(ISeasonalInformation.class, Map.of());
        return new ConditionEvaluator(log, new PlatformFunctions(clock),
                new BiomeVariables(Fakes.of(IBiomeLibrary.class, Map.of())), new DimensionVariables(),
                new DiurnalVariables(oracle), new PlayerVariables(oracle),
                new WeatherVariables(oracle, seasons, Fakes.of(IWeatherSimulation.class, Map.of()), null, new RainbowState()),
                new EnvironmentState(null), new GlobalVariables(config), new SeasonVariables(seasons));
    }
}

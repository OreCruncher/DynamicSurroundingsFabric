// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.variables;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.lib.scripting.IScriptVariable;
import org.orecruncher.dsurround.lib.scripting.ScriptFunction;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.runtime.weather.RainbowState;
import org.orecruncher.dsurround.testing.Fakes;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the weather script variables.
 */
public class WeatherVariablesTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Map<String, ScriptFunction> configure(WeatherVariables variables) {
        var functions = new HashMap<String, ScriptFunction>();
        variables.configure(new IConfigureDefinition() {
            @Override
            public void defineFunction(@NotNull ScriptFunction function) {
                functions.put(function.name(), function);
            }

            @Override
            public void defineVariable(@NotNull String name, @NotNull IScriptVariable delegate) {
            }
        });
        return functions;
    }

    @Test
    void isRainbowIsDefinedAndStartsFalse() {
        // Its value comes from the state on the variables' tick, which needs a world (not in tests)
        var state = new RainbowState();
        state.setShowing(true);
        var variables = new WeatherVariables(Fakes.of(ILevelOracle.class, Map.of()), Fakes.of(ISeasonalInformation.class, Map.of()),
                Fakes.of(IWeatherSimulation.class, Map.of()), null, state);
        assertTrue(configure(variables).containsKey("weather.isRainbow"));
        assertFalse(variables.isRainbow());
    }
}

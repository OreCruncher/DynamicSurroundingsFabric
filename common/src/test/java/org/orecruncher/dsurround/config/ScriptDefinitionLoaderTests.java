// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.DiscoveredResource;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.scripting.ScriptDefinitions;
import org.orecruncher.dsurround.lib.scripting.ScriptDefinitions.Section;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.lib.system.ISystemClock;
import org.orecruncher.dsurround.lib.threading.RecordingLog;
import org.orecruncher.dsurround.runtime.BiomeConditionEvaluator;
import org.orecruncher.dsurround.runtime.ConditionEvaluator;
import org.orecruncher.dsurround.runtime.PlatformFunctions;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;
import org.orecruncher.dsurround.runtime.variables.*;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.testing.Fakes;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for reading scripts.json files: their sections, what a later file replaces, what is refused, and the file the
 * mod ships, through the game's own script engines.
 */
public class ScriptDefinitionLoaderTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final String MOD_FILE = "/assets/dsurround/dsconfigs/scripts.json";

    private final List<String> problems = new ArrayList<>();
    private final List<String> replaced = new ArrayList<>();

    private static DiscoveredResource<JsonElement> file(String namespace, String json) {
        return new DiscoveredResource<>(namespace, JsonParser.parseString(json));
    }

    private static DiscoveredResource<JsonElement> modFile() {
        try (var stream = ScriptDefinitionLoaderTests.class.getResourceAsStream(MOD_FILE)) {
            return new DiscoveredResource<>("dsurround", JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SafeVarargs
    private ScriptDefinitions load(DiscoveredResource<JsonElement>... files) {
        return ScriptDefinitionLoader.load(List.of(files), this.problems::add, this.replaced::add);
    }

    @Test
    void sectionsGiveConstantsAndEachKindOfFunction() {
        var definitions = this.load(file("dsurround", """
                {
                  "_comment": "skipped",
                  "constants": { "CHANCE": 0.1, "ON": true, "NAME": "ghost", "_note": "skipped" },
                  "biome": { "isHaunted": "SPOOKY" },
                  "conditions": { "isHalloween": "platform.isCurrentDateInRangeOf(10, 31, 7)" }
                }"""));
        assertEquals(List.of(), this.problems);
        var all = definitions.definitions();
        assertEquals(5, all.size());
        assertEquals(0.1D, all.get("def.CHANCE").value());
        assertEquals(true, all.get("def.ON").value());
        assertEquals("ghost", all.get("def.NAME").value());
        assertEquals(Section.BIOME, all.get("def.isHaunted").section());
        assertEquals(Section.CONDITIONS, all.get("def.isHalloween").section());
        assertEquals("[dsurround] scripts.json", all.get("def.CHANCE").source());
    }

    @Test
    void aLaterFileReplacesOursWhateverItWas() {
        // Listed alphabetically, as the resource manager lists them: ours still comes first
        var definitions = this.load(
                file("apack", """
                        { "conditions": { "CHANCE": "0.5" }, "constants": { "isHalloween": false } }"""),
                file("dsurround", """
                        { "constants": { "CHANCE": 0.1 }, "conditions": { "isHalloween": "true", "kept": "1" } }"""));
        var all = definitions.definitions();
        assertEquals(Section.CONDITIONS, all.get("def.CHANCE").section(), "a constant replaced by a function");
        assertEquals(false, all.get("def.isHalloween").value());
        assertEquals("1", all.get("def.kept").value());
        assertEquals(List.of(
                "def.CHANCE from [apack] scripts.json replaces the constant from [dsurround] scripts.json",
                "def.isHalloween from [apack] scripts.json replaces the conditions function from [dsurround] scripts.json"), this.replaced);
    }

    @Test
    void whatCantBeADefinitionIsDescribedAndLeftOut() {
        var definitions = this.load(file("apack", """
                {
                  "functions": { "x": "1" },
                  "biome": [ "SPOOKY" ],
                  "constants": { "def.CHANCE": 1, "2fast": 1, "LIST": [1], "NOTHING": null },
                  "conditions": { "blank": " ", "number": 3, "fine": "true" }
                }"""));
        assertEquals(List.of("def.fine"), List.copyOf(definitions.definitions().keySet()));
        assertEquals(8, this.problems.size(), this.problems.toString());
        assertTrue(this.problems.stream().allMatch(p -> p.startsWith("[apack] scripts.json: ")), this.problems.toString());
        assertTrue(this.problems.contains("[apack] scripts.json: unknown section 'functions' (constants, biome or conditions)"));
        assertTrue(this.problems.contains("[apack] scripts.json: constant def.LIST must be true or false, a number, or a string"));
        assertTrue(this.problems.contains("[apack] scripts.json: def.number must be an expression, in a string"));
    }

    @Test
    void aFileThatIsNotAnObjectIsDescribed() {
        this.load(file("apack", "[1, 2]"));
        assertEquals(List.of("[apack] scripts.json: not an object of sections"), this.problems);
    }

    // The game's conditions engine, with every variable set, and the time of day it is told
    private static ConditionEvaluator conditions(RecordingLog log, String now) {
        var oracle = Fakes.of(ILevelOracle.class, Map.of());
        var seasons = Fakes.of(ISeasonalInformation.class, Map.of());
        var clock = Fakes.of(ISystemClock.class, Map.of("getUtcNow", args -> Instant.parse(now)));
        return new ConditionEvaluator(log, new PlatformFunctions(clock),
                new BiomeVariables(Fakes.of(IBiomeLibrary.class, Map.of())), new DimensionVariables(),
                new DiurnalVariables(oracle), new PlayerVariables(oracle),
                new WeatherVariables(oracle, seasons, Fakes.of(IWeatherSimulation.class, Map.of()), null),
                new EnvironmentState(null), new GlobalVariables(new Configuration()), new SeasonVariables(seasons));
    }

    @Test
    void theShippedDefinitionsAllWorkInTheGamesEngines() {
        var definitions = this.load(modFile());
        assertEquals(List.of(), this.problems, "the file reads cleanly");
        assertFalse(definitions.definitions().isEmpty());

        var log = new RecordingLog();
        var halloween = conditions(log, "2026-10-31T12:00:00Z");
        halloween.defineScripts(definitions, this.problems::add);
        var biomes = new BiomeConditionEvaluator(Fakes.of(IBiomeLibrary.class, Map.of()), log, new PlatformFunctions(Fakes.of(ISystemClock.class, Map.of())));
        biomes.define(definitions, this.problems::add);
        assertEquals(List.of(), this.problems, "every function compiles in its own engine");

        // The apparition rules in biomes.json
        var chance = new Script("def.APPARITION_CHANCE * def.halloweenFactor()");
        assertEquals(0.3D, (Double) halloween.eval(chance), 1e-9D, "three times as likely at Halloween");
        var march = conditions(log, "2026-03-15T12:00:00Z");
        march.defineScripts(definitions, this.problems::add);
        assertEquals(0.1D, (Double) march.eval(chance), 1e-9D);
        assertEquals(List.of(), log.at(IModLog.Level.ERROR).stream().map(RecordingLog.Entry::message).toList());
    }
}

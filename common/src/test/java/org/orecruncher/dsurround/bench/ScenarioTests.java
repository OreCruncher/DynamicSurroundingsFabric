package org.orecruncher.dsurround.bench;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ScenarioTests {

    private static Scenario parse(String json) {
        return Scenario.parse("test", JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void readsTheSampleScenario() throws Exception {
        // The sample in scripts/bench, from common/ where the tests run
        var scenario = Scenario.read(Path.of("../scripts/bench/dust-low.json"));
        assertEquals("dust-low", scenario.name());
        assertEquals("bench", scenario.world());
        assertEquals(20261030L, scenario.seed().orElseThrow());
        assertEquals("minecraft:desert", scenario.scenes().get("dust").biome());
        assertEquals("weather", scenario.scenes().get("dust").profile());
        assertEquals(List.of("default", "low"), scenario.runs().stream().map(Scenario.Run::label).toList());
        assertEquals(3, scenario.runs().get(1).settings().size());
        assertEquals(1920, scenario.width().orElseThrow());
        assertEquals(1080, scenario.height().orElseThrow());
    }

    @Test
    void runsUseSettingSetsThenTheirOwn() {
        var scenario = parse("""
                {"settingSets": {"off": {"a.x": false, "a.y": false}, "far": {"b.range": 64}},
                 "scenes": {"s": {}},
                 "runs": [{"label": "one", "scene": "s", "use": "off", "settings": {"a.y": true}},
                          {"label": "two", "scene": "s", "use": ["off", "far"]}]}""");
        var one = scenario.runs().get(0).settings();
        assertFalse(one.get("a.x").getAsBoolean());
        assertTrue(one.get("a.y").getAsBoolean());
        assertEquals(64, scenario.runs().get(1).settings().get("b.range").getAsInt());
        assertEquals(3, scenario.runs().get(1).settings().size());
        assertThrows(IllegalArgumentException.class, () -> parse(
                "{\"scenes\": {\"s\": {}}, \"runs\": [{\"scene\": \"s\", \"use\": \"missing\"}]}"));
    }

    @Test
    void scenesMayRunCommandsBeforeTheScreenshot() {
        var scenario = parse("""
                {"scenes": {"storm": {"shotCommands": ["dsweather lightning"], "shotDelay": 8}, "plain": {}},
                 "runs": [{"scene": "storm"}]}""");
        assertEquals(List.of("dsweather lightning"), scenario.scenes().get("storm").shotCommands());
        assertEquals(8, scenario.scenes().get("storm").shotDelay());
        assertEquals(List.of(), scenario.scenes().get("plain").shotCommands());
        assertEquals(0, scenario.scenes().get("plain").shotDelay());
    }

    @Test
    void repeatsAlternateTheOrder() {
        var scenario = parse("""
                {"repeats": 3, "scenes": {"s": {}},
                 "runs": [{"label": "a", "scene": "s"}, {"label": "b", "scene": "s"}]}""");
        assertEquals(List.of("a", "b", "b", "a", "a", "b"), scenario.order().stream().map(Scenario.Run::label).toList());
    }

    @Test
    void defaultsFillWhatIsLeftOut() {
        var scenario = parse("{\"scenes\": {\"s\": {}}, \"runs\": [{\"scene\": \"s\"}]}");
        assertEquals("bench", scenario.world());
        assertTrue(scenario.seed().isEmpty());
        assertEquals(1, scenario.repeats());
        assertTrue(scenario.screenshot());
        assertNull(scenario.vsync());
        assertEquals("general", scenario.scenes().get("s").profile());
        assertEquals("s-0", scenario.runs().getFirst().label());
    }

    @Test
    void aRunMustNameAScene() {
        assertThrows(IllegalArgumentException.class, () -> parse("{\"scenes\": {}, \"runs\": [{\"scene\": \"nowhere\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"scenes\": {\"s\": {}}, \"runs\": []}"));
    }
}

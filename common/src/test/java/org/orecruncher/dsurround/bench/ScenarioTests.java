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

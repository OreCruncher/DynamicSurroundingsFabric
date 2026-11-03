// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.bench;

import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
    void scenesMayPlaySounds() {
        var scenario = parse("""
                {"volume": 0.05,
                 "scenes": {"box": {"sounds": [
                     {"id": "minecraft:block.stone_button.click_on", "perSecond": 20, "minRadius": 1.5, "radius": 5, "seed": 7},
                     {"id": "minecraft:block.beacon.ambient", "count": 8}]}, "plain": {}},
                 "runs": [{"scene": "box"}]}""");
        assertEquals(0.05, scenario.volume().orElseThrow(), 1e-9);
        var sounds = scenario.scenes().get("box").sounds();
        assertEquals(new Scenario.Sounds("minecraft:block.stone_button.click_on", 20, 0, 1.5, 5, 1, 1, 7), sounds.get(0));
        // Left out: no rate, from the player out to 8 blocks, full volume and pitch, seed 1
        assertEquals(new Scenario.Sounds("minecraft:block.beacon.ambient", 0, 8, 0, 8, 1, 1, 1), sounds.get(1));
        assertEquals(List.of(), scenario.scenes().get("plain").sounds());
        assertTrue(parse("{\"scenes\": {\"s\": {}}, \"runs\": [{\"scene\": \"s\"}]}").volume().isEmpty());
    }

    @Test
    void aSoundNeedsAnIdARateOrCountAndAShell() {
        for (var sound : List.of("{\"perSecond\": 1}", "{\"id\": \"a:b\"}", "{\"id\": \"a:b\", \"count\": 1, \"minRadius\": 9, \"radius\": 4}"))
            assertThrows(IllegalArgumentException.class, () -> parse(
                    "{\"scenes\": {\"s\": {\"sounds\": [" + sound + "]}}, \"runs\": [{\"scene\": \"s\"}]}"), sound);
    }

    @Test
    void readsTheSoundScenario() throws Exception {
        var scenario = Scenario.read(Path.of("../scripts/bench/sound.json"));
        assertTrue(scenario.volume().orElseThrow() > 0);
        for (var scene : scenario.scenes().values())
            assertFalse(scene.sounds().isEmpty());
        // Each run of a scene hears the same sounds: the occluded scene's from the same seeds as the box's
        var enclosed = scenario.scenes().get("enclosed").sounds();
        var occluded = scenario.scenes().get("occluded").sounds();
        for (int i = 0; i < enclosed.size(); i++)
            assertEquals(enclosed.get(i).seed(), occluded.get(i).seed());
    }

    @Test
    void aCommandThatHadNothingToChangeHasntFailed() {
        // A scene's fill on its next run finds the scene built; sendFailure wraps the message in a styled one
        var nothing = Component.translatable("commands.fill.failed");
        assertTrue(ScenarioRunner.nothingToChange(List.of(nothing)));
        assertTrue(ScenarioRunner.nothingToChange(List.of(Component.empty().append(nothing).withStyle(ChatFormatting.RED))));
        assertTrue(ScenarioRunner.nothingToChange(List.of(Component.translatable("commands.setblock.failed"))));
        // A fill above the build limit, or nothing said at all, has failed
        assertFalse(ScenarioRunner.nothingToChange(List.of(Component.translatable("argument.pos.outofworld"))));
        assertFalse(ScenarioRunner.nothingToChange(List.of()));
        assertFalse(ScenarioRunner.nothingToChange(List.of(nothing, Component.literal("Unknown block type"))));
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

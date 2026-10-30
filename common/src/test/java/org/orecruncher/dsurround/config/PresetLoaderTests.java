package org.orecruncher.dsurround.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.config.ConfigPreset;
import org.orecruncher.dsurround.lib.resources.DiscoveredResource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class PresetLoaderTests {

    // Configuration's enums use game classes
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final String MOD_FILE = "/assets/dsurround/dsconfigs/presets.json";

    private static JsonElement modFile() {
        try (var stream = PresetLoaderTests.class.getResourceAsStream(MOD_FILE)) {
            // Lenient, as the resource finders read: the file has comments
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The mod's own presets, as the loader gives them to the configuration with no modpack's files.
     */
    public static List<ConfigPreset> modPresets(Configuration config) {
        var problems = new ArrayList<String>();
        var presets = PresetLoader.load(config, List.of(new DiscoveredResource<>("dsurround", modFile())), problems::add);
        assertEquals(List.of(), problems);
        return presets;
    }

    private static List<ConfigPreset> modPresets() {
        return modPresets(new Configuration());
    }

    private static ConfigPreset modPreset(String id) {
        return modPresets().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    private static DiscoveredResource<JsonElement> file(String namespace, String json) {
        return new DiscoveredResource<>(namespace, JsonParser.parseString(json));
    }

    @Test
    void theModOffersItsPresetsInOrder() {
        assertEquals(List.of("vanillaPlus", "atmosphere", "world", "rpg", "defaults", "low", "medium", "high"),
                modPresets().stream().map(ConfigPreset::id).toList());
        assertEquals(List.of(ConfigPreset.FEATURES, "performance"), ConfigPreset.groups(modPresets()));
    }

    // What the performance presets set that the feature presets may set too: details that are features as well
    // (accepted: a feature preset after Low turns them back on). Quality and ranges are the performance presets' alone
    private static final List<String> SHARED_WITH_FEATURES = List.of("waterfallOptions.enableMist",
            "waterfallOptions.enableFroth", "weatherOptions.enableDynamicClouds", "flameJetOptions.enableLight",
            "fireflyOptions.enableLight");

    @Test
    void thePerformancePresetsSetTheSameSettings() {
        // So any of them, applied after another, leaves nothing of it behind
        var low = modPreset("low").values().keySet();
        assertEquals(low, modPreset("medium").values().keySet());
        assertEquals(low, modPreset("high").values().keySet());
    }

    @Test
    void mediumIsTheDefaults() {
        var config = new Configuration();
        modPreset("low").apply(config);
        modPreset("medium").apply(config);

        var gson = new Gson();
        assertEquals(gson.toJson(new Configuration()), gson.toJson(config));
        assertEquals(List.of(), modPreset("medium").changes(new Configuration()));
    }

    @Test
    void lowIsCheaperAndHighFiner() {
        var config = new Configuration();
        modPreset("low").apply(config);
        assertEquals(GroundFogQuality.FAST, config.weatherOptions.groundFogQuality);
        assertTrue(config.weatherOptions.enableGroundFog, "Low leaves the ground fog's switch, a feature, alone");
        assertFalse(config.waterfallOptions.enableMist);

        modPreset("high").apply(config);
        assertEquals(GroundFogQuality.FABULOUS, config.weatherOptions.groundFogQuality);
        assertTrue(config.blockEffects.blockEffectRange > new Configuration().blockEffects.blockEffectRange);
    }

    @Test
    void featurePresetsLeaveQualityAndRangesToThePerformanceOnes() {
        var performance = modPreset("low").values().keySet();
        for (var preset : modPresets()) {
            if (!preset.group().equals(ConfigPreset.FEATURES))
                continue;
            for (var path : preset.values().keySet())
                assertTrue(!performance.contains(path) || SHARED_WITH_FEATURES.contains(path), preset.id() + " sets " + path);
        }
    }

    @Test
    void everyModPresetValueApplies() {
        // modPresets() fails on any value the loader leaves out; applying them must then find nothing wrong either
        for (var preset : modPresets())
            assertEquals(List.of(), preset.apply(new Configuration()), "preset " + preset.id());
    }

    @Test
    void everyModPresetHasANameAndDescription() throws IOException {
        var keys = new HashSet<String>();
        try (var stream = PresetLoaderTests.class.getResourceAsStream("/assets/dsurround/lang/en_us.json")) {
            var json = JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8));
            keys.addAll(json.getAsJsonObject().keySet());
        }
        var config = new Configuration();
        for (var preset : modPresets()) {
            var key = preset.languageKey(config);
            assertTrue(keys.contains(key), "missing " + key);
            assertTrue(keys.contains(key + ".tooltip"), "missing " + key + ".tooltip");
            // Ours are translated, not named in the file
            assertNull(preset.name());
            assertNull(preset.description());
        }
        for (var group : ConfigPreset.groups(modPresets())) {
            var key = ConfigPreset.groupLanguageKey(config, group);
            assertTrue(keys.contains(key), "missing " + key);
            assertTrue(keys.contains(key + ".tooltip"), "missing " + key + ".tooltip");
        }
        for (var key : List.of("presets.confirm", "presets.confirm.message", "presets.confirm.none", "presets.confirm.unsaved"))
            assertTrue(keys.contains("dsurround.config." + key), "missing " + key);
    }

    @Test
    void defaultsAfterAnyFeaturePresetsAreTheDefaults() {
        var gson = new Gson();
        var defaults = gson.toJson(new Configuration());
        var features = List.of("vanillaPlus", "atmosphere", "world", "rpg");
        // Each alone, and all of them in turn
        for (int n = 0; n <= features.size(); n++) {
            var applied = n < features.size() ? List.of(features.get(n)) : features;
            var config = new Configuration();
            applied.forEach(id -> modPreset(id).apply(config));
            modPreset("defaults").apply(config);
            assertEquals(defaults, gson.toJson(config), "Defaults after " + applied);
        }
    }

    @Test
    void theConfirmationListsWhatVanillaPlusChanges() {
        var config = new Configuration();
        var vanillaPlus = modPreset("vanillaPlus");
        var changes = vanillaPlus.changes(config);
        // From the defaults: all but what is already as it wants (the jump sound, off by default, and sound polish)
        assertFalse(changes.isEmpty());
        assertTrue(changes.size() < vanillaPlus.values().size());
        assertTrue(changes.stream().allMatch(c -> c.group() != null));

        // Applied, nothing is left to change; Defaults on the defaults changes nothing either
        vanillaPlus.apply(config);
        assertEquals(List.of(), vanillaPlus.changes(config));
        assertEquals(List.of(), modPreset("defaults").changes(new Configuration()));
    }

    @Test
    void defaultsCoverWhatEveryFeaturePresetSets() {
        var defaults = modPreset("defaults").values().keySet();
        for (var id : List.of("vanillaPlus", "atmosphere", "world", "rpg"))
            for (var path : modPreset(id).values().keySet())
                assertTrue(defaults.contains(path), "Defaults leaves " + path + " as " + id + " set it");
    }

    @Test
    void presetsAddUp() {
        var config = new Configuration();
        modPreset("vanillaPlus").apply(config);
        modPreset("rpg").apply(config);

        // RPG's feedback on, in Vanilla+'s plain world
        assertTrue(config.entityEffects.enableDamagePopoffs);
        assertTrue(config.entityEffects.showCritWords);
        assertTrue(config.entityEffects.enableJumpSound);
        assertTrue(config.entityEffects.enableBurningEffect);
        assertFalse(config.weatherOptions.enableParticlePrecipitation);
        assertFalse(config.weatherOptions.enableGroundFog);
        assertFalse(config.fireflyOptions.enableFireflies);
        assertTrue(config.footstepAccents.enableAccents);

        modPreset("atmosphere").apply(config);
        assertTrue(config.weatherOptions.enableParticlePrecipitation);
        assertTrue(config.weatherOptions.enableGroundFog);
        assertTrue(config.entityEffects.showCritWords);
    }

    @Test
    void themesDoNotOverlap() {
        // Atmosphere, World and RPG cover different properties, so applying one never undoes another
        var themes = List.of("atmosphere", "world", "rpg");
        for (int i = 0; i < themes.size(); i++)
            for (int j = i + 1; j < themes.size(); j++) {
                var one = modPreset(themes.get(i)).values().keySet();
                for (var path : modPreset(themes.get(j)).values().keySet())
                    assertFalse(one.contains(path), path + " is in both " + themes.get(i) + " and " + themes.get(j));
            }
    }

    @Test
    void footprintsAndSkullFlamesAreRpgAndRipplesAtmosphere() {
        assertTrue(modPreset("rpg").values().containsKey("entityEffects.enableFootprints"));
        assertTrue(modPreset("rpg").values().containsKey("blockEffects.skullFlamesEnabled"));
        assertTrue(modPreset("atmosphere").values().containsKey("blockEffects.waterRippleStyle"));
    }

    @Test
    void ourPresetsLeaveTheHudAlone() {
        for (var preset : modPresets())
            for (var path : preset.values().keySet())
                assertFalse(path.startsWith("compassAndClockOptions.") || path.startsWith("lightLevelOptions."),
                        preset.id() + " sets " + path);
    }

    @Test
    void aModpackAddsReplacesAndHides() {
        var config = new Configuration();
        // Listed alphabetically by the resource manager: the pack's namespace before ours
        var files = List.of(
                file("apack", """
                        [{"id": "cozy", "name": "Cozy", "values": {"fireflyOptions.enableFireflies": true}},
                         {"id": "rpg", "name": "Their RPG", "values": {"entityEffects.enableDamagePopoffs": true}},
                         {"id": "defaults", "hidden": true}]"""),
                new DiscoveredResource<>("dsurround", modFile()));

        var presets = PresetLoader.load(config, files, p -> fail(p));
        assertEquals(List.of("vanillaPlus", "atmosphere", "world", "rpg", "low", "medium", "high", "cozy"),
                presets.stream().map(ConfigPreset::id).toList());
        var rpg = presets.get(3);
        assertEquals("Their RPG", rpg.displayName(config));
        assertEquals(List.of("entityEffects.enableDamagePopoffs"), List.copyOf(rpg.values().keySet()));
    }

    @Test
    void aModpackCannotChangeScaryContentOrRestartSettings() {
        var problems = new ArrayList<String>();
        var presets = PresetLoader.load(new Configuration(), List.of(file("apack", """
                [{"id": "spooky", "values": {"scaryContent.allowScaryContent": true,
                  "musicManagerOptions.replaceMusicManager": false, "entityEffects.enableDeathPuffs": true,
                  "entityEffects.enableDeathPufs": true}}]""")), problems::add);

        assertEquals(List.of("entityEffects.enableDeathPuffs"), List.copyOf(presets.getFirst().values().keySet()));
        assertEquals(3, problems.size(), problems.toString());
        assertTrue(problems.stream().allMatch(p -> p.startsWith("[apack] presets.json: ")), problems.toString());
    }

    @Test
    void aBrokenModpackFileKeepsOurs() {
        var problems = new ArrayList<String>();
        var presets = PresetLoader.load(new Configuration(),
                List.of(new DiscoveredResource<>("dsurround", modFile()), file("apack", "{\"id\": \"oops\"}")), problems::add);

        assertEquals(8, presets.size());
        assertEquals(1, problems.size());
    }
}

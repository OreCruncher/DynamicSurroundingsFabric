package org.orecruncher.dsurround.lib.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigPresetTests {

    public static class TestConfig extends ConfigurationData {

        public enum Mode {OFF, LOW, HIGH}

        public static class Group {
            @Property
            public boolean enabled = true;

            @Property
            @IntegerRange(min = 0, max = 10)
            public int count = 5;

            @Property
            public double scale = 1D;

            @Property
            public Mode mode = Mode.HIGH;

            @Property
            public String name = "x";

            @Property
            @RestartRequired
            public boolean restart = false;
        }

        @Property
        public final Group group = new Group();

        @Property
        public boolean top = true;
    }

    private static List<ConfigPreset> parse(String json, List<String> problems) {
        return ConfigPreset.parse(JsonParser.parseString(json), problems::add);
    }

    private static List<ConfigPreset> parse(String json) {
        var problems = new ArrayList<String>();
        var presets = parse(json, problems);
        assertEquals(List.of(), problems);
        return presets;
    }

    private static ConfigPreset preset(String values) {
        return parse("[{\"id\": \"p\", \"values\": " + values + "}]").getFirst();
    }

    private static ConfigPreset preset(String id, String values) {
        return parse("[{\"id\": \"" + id + "\", \"values\": " + values + "}]").getFirst();
    }

    @Test
    void parsesPresetsInOrder() {
        var presets = parse("""
                // comments are allowed
                [
                  {"id": "first", "values": {"top": false}},
                  {"id": "second", "name": "Second", "description": "The second", "values": {"group.count": 3, "group.enabled": false}}
                ]""");
        assertEquals(List.of("first", "second"), presets.stream().map(ConfigPreset::id).toList());
        assertEquals(List.of("group.count", "group.enabled"), List.copyOf(presets.get(1).values().keySet()));
        assertNull(presets.getFirst().name());
        assertEquals("Second", presets.get(1).name());
        assertEquals("The second", presets.get(1).description());
    }

    @Test
    void skipsMalformedEntriesAndKeepsTheRest() {
        var problems = new ArrayList<String>();
        var presets = parse("""
                [{"values": {}}, {"id": "no values"}, {"id": "bad id!", "values": {}}, 1,
                 {"id": "good", "values": {"top": false}}]""", problems);

        assertEquals(List.of("good"), presets.stream().map(ConfigPreset::id).toList());
        assertEquals(4, problems.size(), problems.toString());
    }

    @Test
    void aFileThatIsNotAnArrayHasNoPresets() {
        var problems = new ArrayList<String>();
        assertEquals(List.of(), parse("{}", problems));
        assertEquals(1, problems.size());
    }

    @Test
    void presetsAreGroupedInTheOrderTheyNameGroups() {
        var presets = parse("""
                [{"id": "a", "values": {}}, {"id": "b", "group": "speed", "values": {}}, {"id": "c", "values": {}},
                 {"id": "d", "group": "speed", "values": {}}]""");
        assertEquals(ConfigPreset.FEATURES, presets.getFirst().group());
        assertEquals(List.of(ConfigPreset.FEATURES, "speed"), ConfigPreset.groups(presets));
        // No translation in tests: the group's name
        assertEquals("speed", ConfigPreset.displayGroup(new TestConfig(), "speed"));
    }

    @Test
    void aBadGroupIsSkipped() {
        var problems = new ArrayList<String>();
        assertEquals(List.of(), parse("[{\"id\": \"a\", \"group\": \"no good\", \"values\": {}}]", problems));
        assertEquals(1, problems.size());
    }

    @Test
    void validatingAndCurrentValuesKeepTheGroup() {
        var config = new TestConfig();
        var preset = parse("[{\"id\": \"a\", \"group\": \"speed\", \"values\": {\"top\": false}}]").getFirst();
        assertEquals("speed", preset.validated(config, p -> true, p -> {}).group());
        assertEquals("speed", preset.current(config).group());
    }

    @Test
    void aHiddenEntryNeedsNoValues() {
        var hidden = parse("[{\"id\": \"rpg\", \"hidden\": true}]").getFirst();
        assertTrue(hidden.hidden());
    }

    @Test
    void setsEachTypeOfValue() {
        var config = new TestConfig();
        var problems = preset("""
                {"top": false, "group.enabled": false, "group.count": 7, "group.scale": 2.5,
                 "group.mode": "LOW", "group.name": "y"}""").apply(config);

        assertEquals(List.of(), problems);
        assertFalse(config.top);
        assertFalse(config.group.enabled);
        assertEquals(7, config.group.count);
        assertEquals(2.5, config.group.scale);
        assertEquals(TestConfig.Mode.LOW, config.group.mode);
        assertEquals("y", config.group.name);
    }

    @Test
    void leavesWhatItDoesNotList() {
        var config = new TestConfig();
        config.group.count = 2;
        config.group.mode = TestConfig.Mode.OFF;
        preset("{\"group.enabled\": false}").apply(config);

        assertFalse(config.group.enabled);
        assertEquals(2, config.group.count);
        assertEquals(TestConfig.Mode.OFF, config.group.mode);
        assertTrue(config.top);
    }

    @Test
    void skipsValuesThatDoNotFit() {
        var config = new TestConfig();
        var problems = preset("""
                {"missing": true, "group": true, "top.deeper": true, "group.enabled": "yes",
                 "group.count": 2.5, "group.mode": "MEDIUM", "group.name": 3, "group.scale": [1]}""").apply(config);

        assertEquals(8, problems.size(), problems.toString());
        assertTrue(config.group.enabled);
        assertEquals(5, config.group.count);
        assertEquals(TestConfig.Mode.HIGH, config.group.mode);
        assertEquals("x", config.group.name);
        assertEquals(1D, config.group.scale);
    }

    @Test
    void limitsValuesToTheirRange() {
        var config = new TestConfig();
        var problems = preset("{\"group.count\": 50}").apply(config);

        assertEquals(1, problems.size());
        assertEquals(10, config.group.count);
    }

    @Test
    void listsOnlyWhatWouldChange() {
        var config = new TestConfig();
        var changes = preset("""
                {"group.mode": "LOW", "top": true, "missing": true, "group.count": 50, "group.enabled": "yes"}""")
                .changes(config);

        // The value already matching, and those apply() leaves out, aren't listed; one out of range is limited
        assertEquals(2, changes.size(), changes.toString());
        var mode = changes.getFirst();
        assertEquals("mode", mode.property().fieldName());
        assertEquals("group", mode.group().fieldName());
        assertEquals(TestConfig.Mode.HIGH, mode.from());
        assertEquals(TestConfig.Mode.LOW, mode.to());
        var count = changes.get(1);
        assertEquals(5, count.from());
        assertEquals(10, count.to());
        // Listing changes nothing
        assertEquals(TestConfig.Mode.HIGH, config.group.mode);
    }

    @Test
    void theCurrentValuesPutTheSettingsBack() {
        var config = new TestConfig();
        var preset = preset("{\"top\": false, \"group.count\": 7, \"group.mode\": \"LOW\", \"group.scale\": 2.5, \"missing\": 1}");
        var before = preset.current(config);
        assertEquals(List.of("top", "group.count", "group.mode", "group.scale"), List.copyOf(before.values().keySet()));

        preset.apply(config);
        assertEquals(List.of(), before.apply(config));
        assertTrue(config.top);
        assertEquals(5, config.group.count);
        assertEquals(TestConfig.Mode.HIGH, config.group.mode);
        assertEquals(1D, config.group.scale);
    }

    @Test
    void aChangeAtTheTopHasNoGroup() {
        var change = preset("{\"top\": false}").changes(new TestConfig()).getFirst();
        assertNull(change.group());
        assertEquals(true, change.from());
        assertEquals(false, change.to());
    }

    @Test
    void validationKeepsOnlyWhatCanApply() {
        var problems = new ArrayList<String>();
        var validated = preset("""
                {"top": false, "missing": true, "group.count": 50, "group.mode": "MEDIUM",
                 "group.restart": true, "group.name": "y"}""")
                .validated(new TestConfig(), path -> !path.equals("group.name"), problems::add);

        assertEquals(List.of("top"), List.copyOf(validated.values().keySet()));
        assertEquals(5, problems.size(), problems.toString());
    }

    @Test
    void mergingAddsReplacesAndHides() {
        var ours = List.of(preset("a", "{\"top\": false}"), preset("b", "{\"top\": false}"), preset("c", "{\"top\": false}"));
        var theirs = parse("""
                [{"id": "b", "name": "Their b", "values": {"top": true}},
                 {"id": "c", "hidden": true},
                 {"id": "d", "values": {}}]""");

        var merged = ConfigPreset.merge(List.of(ours, theirs));
        assertEquals(List.of("a", "b", "d"), merged.stream().map(ConfigPreset::id).toList());
        assertEquals("Their b", merged.get(1).name());
    }

    @Test
    void namesComeFromTheFileThenTheTranslationThenTheId() {
        var config = new TestConfig();
        var named = parse("[{\"id\": \"p\", \"name\": \"Mine\", \"description\": \"Does things\", \"values\": {}}]").getFirst();
        assertEquals("Mine", named.displayName(config));
        assertEquals("Does things", named.displayDescription(config));

        // No translation in tests: the id, and no description
        var plain = preset("plain", "{}");
        assertEquals("plain", plain.displayName(config));
        assertEquals("", plain.displayDescription(config));
    }

    @Test
    void aConfigurationHasNoPresetsUntilSet() {
        var config = new TestConfig();
        assertEquals(List.of(), config.getPresets());

        config.setPresets(List.of(preset("p", "{}")));
        assertTrue(config.getPreset("p").isPresent());
        assertTrue(config.getPreset("q").isEmpty());
    }

    @Test
    void presetsAreNotSavedWithTheConfiguration() {
        var config = new TestConfig();
        config.setPresets(List.of(preset("p", "{}")));
        assertFalse(CommentedJson.write(config).contains("presets"));
    }

    @Test
    void presetValuesAreKeptInOrderAndFixed() {
        var map = preset("{\"top\": false, \"group.count\": 1}").values();
        assertEquals("top", map.keySet().iterator().next());
        assertThrows(UnsupportedOperationException.class, () -> map.put("x", null));
    }
}

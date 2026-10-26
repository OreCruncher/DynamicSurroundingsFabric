package org.orecruncher.dsurround.lib.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.ClockStyle;
import org.orecruncher.dsurround.config.CompassStyle;
import org.orecruncher.dsurround.config.GroundFogQuality;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class MovedPropertiesTests {

    @ConfigPlacement(folderName = "test", fileName = "moved")
    public static class MovedConfig extends ConfigurationData {

        public static class OldGroup {
            @Property
            public int other = 1;
        }

        public static class NewGroup {
            @Property
            @MovedFrom("oldGroup.speed")
            public int speed = 5;

            @Property
            @MovedFrom("oldGroup.enabled")
            public boolean enabled = true;

            // Was a switch: off becomes OFF, and on gets the default
            @Property
            @MovedFrom(value = "oldGroup.switched", values = "false=OFF")
            public Mode mode = Mode.HIGH;

            // A switch folded into a property that was already there: off replaces it, on leaves it
            @Property
            @MovedFrom(value = "oldGroup.shown", values = "false=OFF", replaces = true)
            public Mode style = Mode.HIGH;

            @Property
            public final InnerGroup inner = new InnerGroup();
        }

        // A group within a group, which a setting can move into
        public static class InnerGroup {
            @Property
            @MovedFrom("newGroup.bands")
            public int bands = 3;
        }

        public enum Mode {
            OFF, LOW, HIGH
        }

        @Property
        public final OldGroup oldGroup = new OldGroup();

        @Property
        public final NewGroup newGroup = new NewGroup();

        @Property
        @MovedFrom("legacyTop")
        public int top = 3;

        // Moved twice: most recently from newGroup.interim, before that from oldGroup.ancient
        @Property
        @MovedFrom({"newGroup.interim", "oldGroup.ancient"})
        public int twice = 1;
    }

    @TempDir
    Path folder;

    private MovedConfig load(String json) throws IOException {
        var file = this.folder.resolve("moved.json");
        Files.writeString(file, json);
        return ConfigurationData.load(MovedConfig.class, file);
    }

    private JsonObject written(String name) throws IOException {
        return JsonParser.parseReader(new StringReader(Files.readString(this.folder.resolve(name)))).getAsJsonObject();
    }

    // ---- Through loading

    @Test
    void anOldValueIsCarriedOver() throws IOException {
        var config = load("{\"oldGroup\": {\"speed\": 9, \"enabled\": false, \"other\": 2}}");
        assertEquals(9, config.newGroup.speed);
        assertFalse(config.newGroup.enabled);
        // What didn't move stays where it is
        assertEquals(2, config.oldGroup.other);
    }

    @Test
    void aValueAtTheNewPlaceWins() throws IOException {
        var config = load("{\"oldGroup\": {\"speed\": 9}, \"newGroup\": {\"speed\": 7}}");
        assertEquals(7, config.newGroup.speed);
    }

    @Test
    void nothingAtEitherPlaceGivesTheDefault() throws IOException {
        var config = load("{\"oldGroup\": {\"other\": 2}}");
        assertEquals(5, config.newGroup.speed);
        assertTrue(config.newGroup.enabled);
    }

    @Test
    void aTopLevelPropertyCanMove() throws IOException {
        assertEquals(11, load("{\"legacyTop\": 11}").top);
    }

    @Test
    void aPropertyCanMoveIntoAGroupWithinAGroup() throws IOException {
        var config = load("{\"newGroup\": {\"bands\": 1}}");
        assertEquals(1, config.newGroup.inner.bands);
        var json = written("moved.json").getAsJsonObject("newGroup");
        assertFalse(json.has("bands"), "the old place is still written");
        assertEquals(1, json.getAsJsonObject("inner").get("bands").getAsInt());
    }

    @Test
    void theFileIsWrittenWithTheNewPlaceOnly() throws IOException {
        load("{\"oldGroup\": {\"speed\": 9, \"other\": 2}, \"legacyTop\": 11}");
        var json = written("moved.json");
        assertEquals(9, json.getAsJsonObject("newGroup").get("speed").getAsInt());
        assertFalse(json.getAsJsonObject("oldGroup").has("speed"), "the old place is still written");
        assertFalse(json.has("legacyTop"));
        assertEquals(11, json.get("top").getAsInt());
    }

    @Test
    void theFirstOldPlaceWithAValueWins() throws IOException {
        assertEquals(8, load("{\"newGroup\": {\"interim\": 8}, \"oldGroup\": {\"ancient\": 4}}").twice);
    }

    @Test
    void laterOldPlacesAreFallbacks() throws IOException {
        // A file from before the first move
        assertEquals(4, load("{\"oldGroup\": {\"ancient\": 4}}").twice);
    }

    @Test
    void aValueIsConvertedAsItsPairSays() throws IOException {
        assertEquals(MovedConfig.Mode.OFF, load("{\"oldGroup\": {\"switched\": false}}").newGroup.mode);
        assertFalse(written("moved.json").getAsJsonObject("oldGroup").has("switched"), "the old place is still written");
    }

    @Test
    void aValueWithNoPairGetsTheDefault() throws IOException {
        // Regression: a switch's true carried over as it was can't be read as an enum, and the whole file was set aside
        assertEquals(MovedConfig.Mode.HIGH, load("{\"oldGroup\": {\"switched\": true, \"other\": 2}}").newGroup.mode);
        try (var files = Files.list(this.folder)) {
            assertFalse(files.anyMatch(f -> f.getFileName().toString().endsWith(".bad")), "the file was set aside");
        }
    }

    @Test
    void aReplacingValueOverridesTheNewPlace() throws IOException {
        var config = load("{\"oldGroup\": {\"shown\": false}, \"newGroup\": {\"style\": \"LOW\"}}");
        assertEquals(MovedConfig.Mode.OFF, config.newGroup.style);
        assertFalse(written("moved.json").getAsJsonObject("oldGroup").has("shown"), "the old place is still written");
    }

    @Test
    void aReplacingValueWithNoPairLeavesTheNewPlace() throws IOException {
        assertEquals(MovedConfig.Mode.LOW, load("{\"oldGroup\": {\"shown\": true}, \"newGroup\": {\"style\": \"LOW\"}}").newGroup.style);
        assertEquals(MovedConfig.Mode.HIGH, load("{\"oldGroup\": {\"shown\": true}}").newGroup.style, "nothing there: the default");
    }

    // ---- The JSON paths

    @Test
    void setCreatesTheObjectsOnTheWay() {
        var root = new JsonObject();
        MovedProperties.set(root, "a.b.c", new JsonPrimitive(1));
        assertEquals(1, MovedProperties.get(root, "a.b.c").getAsInt());
    }

    @Test
    void pathsThroughSomethingThatIsntAnObjectFindNothing() {
        var root = JsonParser.parseString("{\"a\": 5, \"n\": null}").getAsJsonObject();
        assertNull(MovedProperties.get(root, "a.b"));
        assertNull(MovedProperties.get(root, "n"));
        assertNull(MovedProperties.get(root, "missing.b"));
        // And setting through it leaves it alone
        MovedProperties.set(root, "a.b", new JsonPrimitive(1));
        assertEquals(5, root.get("a").getAsInt());
    }

    // ---- The mod's configuration

    private Configuration loadModConfig(String json) throws IOException {
        var file = this.folder.resolve("dsurround.json");
        Files.writeString(file, json);
        return ConfigurationData.load(Configuration.class, file);
    }

    @Test
    void theBlockEffectSettingsAreCarriedOver() throws IOException {
        // Waterfalls, fireflies and flame jets used to be switched in the block effects
        var config = loadModConfig("""
                {
                  "blockEffects": {
                    "waterfallsEnabled": false,
                    "enableWaterfallSounds": false,
                    "enableWaterfallParticles": false,
                    "firefliesEnabled": false,
                    "flameJetEnabled": false,
                    "flameJetLight": false,
                    "steamColumnEnabled": false
                  }
                }
                """);
        assertFalse(config.waterfallOptions.enableWaterfalls);
        assertFalse(config.waterfallOptions.enableSounds);
        assertFalse(config.waterfallOptions.enableParticles);
        assertFalse(config.fireflyOptions.enableFireflies);
        assertFalse(config.flameJetOptions.enableFlameJets);
        assertFalse(config.flameJetOptions.enableLight);
        // Settings that stayed are read as before
        assertFalse(config.blockEffects.steamColumnEnabled);

        var blockEffects = written("dsurround.json").getAsJsonObject("blockEffects");
        assertFalse(blockEffects.has("waterfallsEnabled") || blockEffects.has("firefliesEnabled") || blockEffects.has("flameJetEnabled"),
                "old settings still written");
    }

    @Test
    void theWorksInProgressSettingsAreCarriedOver() throws IOException {
        var config = loadModConfig("""
                {
                  "worksInProgressOptions": {
                    "enableWaterfallMist": false,
                    "enableWaterStepFroth": false,
                    "enableFireflyGlow": true,
                    "enableFireflyLight": false
                  }
                }
                """);
        assertFalse(config.waterfallOptions.enableMist);
        assertFalse(config.waterfallOptions.enableFroth);
        assertTrue(config.fireflyOptions.enableGlow);
        assertFalse(config.fireflyOptions.enableLight);
        assertFalse(written("dsurround.json").has("worksInProgressOptions"), "the old group is still written");
    }

    @Test
    void theParticleEffectSettingsAreCarriedOverAheadOfOlderOnes() throws IOException {
        // The particle effects group came between works in progress and the waterfall and firefly groups
        var config = loadModConfig("""
                {
                  "particleEffects": { "enableWaterfallMist": false, "enableFireflyLight": true },
                  "worksInProgressOptions": { "enableWaterfallMist": true, "enableFireflyLight": false }
                }
                """);
        assertFalse(config.waterfallOptions.enableMist);
        assertTrue(config.fireflyOptions.enableLight);
        assertFalse(written("dsurround.json").has("particleEffects"));
    }

    @Test
    void theGroundFogSwitchBecomesItsQuality() throws IOException {
        assertEquals(GroundFogQuality.NONE,
                loadModConfig("{\"weatherOptions\": {\"enableGroundFog\": false}}").weatherOptions.groundFogQuality);
        assertEquals(GroundFogQuality.FANCY,
                loadModConfig("{\"weatherOptions\": {\"enableGroundFog\": true}}").weatherOptions.groundFogQuality);
        assertFalse(written("dsurround.json").getAsJsonObject("weatherOptions").has("enableGroundFog"));
    }

    @Test
    void theAuroraBandsMoveToTheClassicAurora() throws IOException {
        assertEquals(1, loadModConfig("{\"auroraOptions\": {\"maxBands\": 1}}").auroraOptions.classic.maxBands);
        var aurora = written("dsurround.json").getAsJsonObject("auroraOptions");
        assertFalse(aurora.has("maxBands"));
        assertEquals(1, aurora.getAsJsonObject("classic").get("maxBands").getAsInt());
    }

    @Test
    void theCompassSwitchBecomesItsStyle() throws IOException {
        // Off wins over the style that was set beside it; on keeps that style
        assertEquals(CompassStyle.NONE, loadModConfig(
                "{\"compassAndClockOptions\": {\"enableCompass\": false, \"compassStyle\": \"ROSE_1\"}}").compassAndClockOptions.compassStyle);
        assertEquals(CompassStyle.ROSE_1, loadModConfig(
                "{\"compassAndClockOptions\": {\"enableCompass\": true, \"compassStyle\": \"ROSE_1\"}}").compassAndClockOptions.compassStyle);
        assertFalse(written("dsurround.json").getAsJsonObject("compassAndClockOptions").has("enableCompass"));
    }

    @Test
    void theClockSwitchBecomesItsStyle() throws IOException {
        assertEquals(ClockStyle.NONE,
                loadModConfig("{\"compassAndClockOptions\": {\"enableClock\": false}}").compassAndClockOptions.clockStyle);
        assertEquals(ClockStyle.POCKET_WATCH,
                loadModConfig("{\"compassAndClockOptions\": {\"enableClock\": true}}").compassAndClockOptions.clockStyle);
        assertFalse(written("dsurround.json").getAsJsonObject("compassAndClockOptions").has("enableClock"));
    }

    @Test
    void theScarySoundsSwitchBecomesTheScaryContentOne() throws IOException {
        var config = loadModConfig("{\"soundOptions\": {\"allowScarySounds\": false, \"remapSounds\": false}}");
        assertFalse(config.scaryContent.allowScaryContent);
        assertFalse(config.scaryContent.lightningAllowed(), "lightning shown with scary content off");
        assertFalse(config.soundOptions.remapSounds);
        assertFalse(written("dsurround.json").getAsJsonObject("soundOptions").has("allowScarySounds"));
    }

    @Test
    void theLoggingGroupBecomesDiagnosticsAndGeneralOptions() throws IOException {
        // The whole group moved, apart from two switches that went to the general options. A 1.21.1 file also has the
        // guards, which 26.2 has no use for
        var config = loadModConfig("""
                {
                  "logging": {
                    "enableDebugLogging": true,
                    "traceMask": 5,
                    "enableModUpdateChatMessage": false,
                    "filteredTagView": false,
                    "registerCommands": false,
                    "voiceChatHearSelf": true,
                    "guards": { "repairParticleBatches": false }
                  }
                }
                """);
        assertTrue(config.diagnostics.enableDebugLogging);
        assertEquals(5, config.diagnostics.traceMask);
        assertFalse(config.diagnostics.filteredTagView);
        assertTrue(config.diagnostics.voiceChatHearSelf);
        assertFalse(config.generalOptions.enableModUpdateChatMessage);
        assertFalse(config.generalOptions.registerCommands);

        var json = written("dsurround.json");
        assertFalse(json.has("logging"), "the old group is still written");
        var diagnostics = json.getAsJsonObject("diagnostics");
        assertFalse(diagnostics.has("guards"), "1.21.1's guards are still written");
        assertFalse(diagnostics.has("enableModUpdateChatMessage") || diagnostics.has("registerCommands"),
                "the general options are still written in the diagnostics");
    }

    @Test
    void lightningNeedsScaryContentAndItsOwnSwitch() throws IOException {
        var config = loadModConfig("{}");
        assertTrue(config.scaryContent.lightningAllowed());
        config.scaryContent.enableLightning = false;
        assertFalse(config.scaryContent.lightningAllowed());
        config.scaryContent.enableLightning = true;
        config.scaryContent.allowScaryContent = false;
        assertFalse(config.scaryContent.lightningAllowed());
    }

    @Test
    void movedFromPathsAreNoLongerProperties() {
        // An old path that is still a property would mean the move wasn't finished (or the path is mistyped)
        var stale = new ArrayList<String>();
        for (var group : Configuration.class.getFields()) {
            if (Modifier.isStatic(group.getModifiers()))
                continue;
            var groupMoved = group.getAnnotation(MovedFrom.class);
            if (groupMoved != null)
                for (var path : groupMoved.value())
                    if (isProperty(path))
                        stale.add(path);
            for (var field : group.getType().getFields()) {
                var moved = field.getAnnotation(MovedFrom.class);
                if (moved == null)
                    continue;
                for (var path : moved.value())
                    if (isProperty(path))
                        stale.add(path);
            }
        }
        assertTrue(stale.isEmpty(), "still properties: " + stale);
    }

    private static boolean isProperty(String path) {
        var parts = path.split("\\.");
        try {
            var group = Configuration.class.getField(parts[0]);
            return parts.length == 1 || group.getType().getField(parts[1]).isAnnotationPresent(Property.class);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
}

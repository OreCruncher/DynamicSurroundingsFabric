package org.orecruncher.dsurround.lib.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.config.ConfigurationData.ConfigPlacement;
import org.orecruncher.dsurround.lib.config.ConfigurationData.MovedFrom;
import org.orecruncher.dsurround.lib.config.ConfigurationData.Property;

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
        // Waterfalls and fireflies used to be switched in the block effects
        var config = loadModConfig("""
                {
                  "blockEffects": {
                    "waterfallsEnabled": false,
                    "enableWaterfallSounds": false,
                    "enableWaterfallParticles": false,
                    "firefliesEnabled": false,
                    "flameJetEnabled": false
                  }
                }
                """);
        assertFalse(config.waterfallOptions.enableWaterfalls);
        assertFalse(config.waterfallOptions.enableSounds);
        assertFalse(config.waterfallOptions.enableParticles);
        assertFalse(config.fireflyOptions.enableFireflies);
        // Settings that stayed are read as before
        assertFalse(config.blockEffects.flameJetEnabled);

        var blockEffects = written("dsurround.json").getAsJsonObject("blockEffects");
        assertFalse(blockEffects.has("waterfallsEnabled") || blockEffects.has("firefliesEnabled"), "old settings still written");
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
    void movedFromPathsAreNoLongerProperties() {
        // An old path that is still a property would mean the move wasn't finished (or the path is mistyped)
        var stale = new ArrayList<String>();
        for (var group : Configuration.class.getFields()) {
            if (Modifier.isStatic(group.getModifiers()))
                continue;
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

package org.orecruncher.dsurround.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for reading and writing an individual sound's configuration, including files written before the action
 * replaced the block and cull flags.
 */
public class IndividualSoundConfigEntryTests {

    private static final ResourceLocation SOUND = ResourceLocation.parse("minecraft:entity.cow.ambient");

    private static IndividualSoundConfigEntry decode(String text) {
        return IndividualSoundConfigEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(text)).getOrThrow();
    }

    private static JsonObject encode(IndividualSoundConfigEntry entry) {
        return IndividualSoundConfigEntry.CODEC.encodeStart(JsonOps.INSTANCE, entry).getOrThrow().getAsJsonObject();
    }

    private static String withSound(String fields) {
        return "{\"soundEventId\": \"" + SOUND + "\"" + fields + "}";
    }

    @Test
    void oldBlockFlagReadsAsBlock() {
        assertEquals(SoundAction.BLOCK, decode(withSound(", \"block\": true")).action);
    }

    @Test
    void oldCullFlagReadsAsCull() {
        assertEquals(SoundAction.CULL, decode(withSound(", \"cull\": true")).action);
    }

    @Test
    void oldFileWithBothFlagsIsCulled() {
        // As the configuration screen always showed such a sound
        assertEquals(SoundAction.CULL, decode(withSound(", \"block\": true, \"cull\": true")).action);
    }

    @Test
    void actionWinsOverOldFlags() {
        assertEquals(SoundAction.BLOCK, decode(withSound(", \"action\": \"block\", \"cull\": true")).action);
    }

    @Test
    void entryWithNothingSetIsDefault() {
        var entry = decode(withSound(""));

        assertEquals(SoundAction.DEFAULT, entry.action);
        assertEquals(100, entry.volumeScale);
        assertFalse(entry.isNotDefault());
    }

    @Test
    void everyActionRoundTrips() {
        for (var action : SoundAction.values()) {
            var entry = new IndividualSoundConfigEntry(SOUND, 50, action);
            var decoded = IndividualSoundConfigEntry.CODEC.parse(JsonOps.INSTANCE, encode(entry)).getOrThrow();

            assertEquals(action, decoded.action);
            assertEquals(50, decoded.volumeScale);
        }
    }

    @Test
    void actionIsWrittenByName() {
        var json = encode(new IndividualSoundConfigEntry(SOUND, 100, SoundAction.CULL));

        assertEquals("cull", json.get("action").getAsString());
    }

    @Test
    void oldFlagsAreNeverWritten() {
        for (var action : SoundAction.values()) {
            var json = encode(new IndividualSoundConfigEntry(SOUND, 100, action));

            assertFalse(json.has("block"), json::toString);
            assertFalse(json.has("cull"), json::toString);
        }
    }

    @Test
    void defaultActionIsLeftOut() {
        var json = encode(new IndividualSoundConfigEntry(SOUND, 50, SoundAction.DEFAULT));

        assertFalse(json.has("action"), json::toString);
    }

    @Test
    void unknownActionIsAnError() {
        var result = IndividualSoundConfigEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(withSound(", \"action\": \"mute\"")));

        assertTrue(result.isError());
    }

    @Test
    void actionMakesAnEntryNotDefault() {
        assertTrue(new IndividualSoundConfigEntry(SOUND, 100, SoundAction.BLOCK).isNotDefault());
        assertTrue(new IndividualSoundConfigEntry(SOUND, 100, SoundAction.CULL).isNotDefault());
    }
}

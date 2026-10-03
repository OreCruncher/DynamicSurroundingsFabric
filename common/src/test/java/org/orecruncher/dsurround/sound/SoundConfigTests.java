package org.orecruncher.dsurround.sound;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.util.valueproviders.UniformFloat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for reading sound factory configuration: volume and pitch ranges, categories, attenuation, and the factory
 * behavior that depends on them.
 */
public class SoundConfigTests {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static FloatProvider range(String text) {
        return SoundCodecHelpers.SOUND_PROPERTY_RANGE.parse(JsonOps.INSTANCE, json(text)).getOrThrow();
    }

    private static SoundFactory factory(String text) {
        return SoundFactory.CODEC.parse(JsonOps.INSTANCE, json(text)).getOrThrow();
    }

    // ---- Volume and pitch ranges -----------------------------------------------------------------------------

    @Test
    void numberIsAFixedValue() {
        var provider = assertInstanceOf(ConstantFloat.class, range("0.5"));
        assertEquals(0.5F, provider.getValue());
    }

    @Test
    void rangeIsUniform() {
        var provider = assertInstanceOf(UniformFloat.class, range("{\"min\": 0.8, \"max\": 1.2}"));
        assertEquals(0.8F, provider.getMinValue());
        assertEquals(1.2F, provider.getMaxValue());
    }

    @Test
    void rangeWithEqualEndsIsAFixedValue() {
        // Regression: these all threw IllegalArgumentException from UniformFloat while loading
        assertEquals(1F, assertInstanceOf(ConstantFloat.class, range("{}")).getValue());
        assertEquals(1F, assertInstanceOf(ConstantFloat.class, range("{\"min\": 1.0}")).getValue());
        assertEquals(1F, assertInstanceOf(ConstantFloat.class, range("{\"max\": 1.0}")).getValue());
        assertEquals(0.8F, assertInstanceOf(ConstantFloat.class, range("{\"min\": 0.8, \"max\": 0.8}")).getValue());
    }

    @Test
    void rangeWithMinAboveMaxIsAnError() {
        // An error the loader can report, not an exception
        var result = SoundCodecHelpers.SOUND_PROPERTY_RANGE.parse(JsonOps.INSTANCE, json("{\"min\": 2.0, \"max\": 1.0}"));

        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("greater than max"), result.toString());
    }

    @Test
    void rangesCanBeWrittenAndReadBack() {
        // Regression: writing threw "Not gonna happen"
        var fixed = SoundCodecHelpers.SOUND_PROPERTY_RANGE.encodeStart(JsonOps.INSTANCE, range("0.5")).getOrThrow();
        assertEquals(0.5F, fixed.getAsFloat(), "a fixed value is written as a number");

        var written = SoundCodecHelpers.SOUND_PROPERTY_RANGE.encodeStart(JsonOps.INSTANCE, range("{\"min\": 0.8, \"max\": 1.2}")).getOrThrow();
        assertEquals(0.8F, written.getAsJsonObject().get("min").getAsFloat());
        assertEquals(1.2F, written.getAsJsonObject().get("max").getAsFloat());

        var reread = assertInstanceOf(UniformFloat.class, SoundCodecHelpers.SOUND_PROPERTY_RANGE.parse(JsonOps.INSTANCE, written).getOrThrow());
        assertEquals(0.8F, reread.getMinValue());
        assertEquals(1.2F, reread.getMaxValue());
    }

    // ---- Category and attenuation ----------------------------------------------------------------------------

    @Test
    void categoryIgnoresCase() {
        // Minecraft's names: block, hostile, neutral, player, ambient, weather, music, record, voice, master
        var result = SoundCodecHelpers.SOUND_CATEGORY_CODEC.parse(JsonOps.INSTANCE, json("\"Block\"")).getOrThrow();
        assertEquals(SoundSource.BLOCKS, result);
    }

    @Test
    void unknownCategoryFallsBackToAmbient() {
        // Also logged as a warning, so the typo is visible
        var result = SoundCodecHelpers.SOUND_CATEGORY_CODEC.parse(JsonOps.INSTANCE, json("\"blokcs\"")).getOrThrow();
        assertEquals(SoundSource.AMBIENT, result);
    }

    @Test
    void attenuationByName() {
        assertEquals(SoundInstance.Attenuation.NONE,
                SoundCodecHelpers.ATTENUATION_CODEC.parse(JsonOps.INSTANCE, json("\"none\"")).getOrThrow());
        assertEquals(SoundInstance.Attenuation.LINEAR,
                SoundCodecHelpers.ATTENUATION_CODEC.parse(JsonOps.INSTANCE, json("\"bogus\"")).getOrThrow(),
                "unknown falls back to linear");
    }

    // ---- Factories -------------------------------------------------------------------------------------------

    @Test
    void defaultsArePositionalAndAttenuated() {
        var f = factory("{\"soundEvent\": \"dsurround:test\"}");

        assertFalse(f.global());
        assertEquals(SoundInstance.Attenuation.LINEAR, f.attenuation());
    }

    @Test
    void globalAndAttenuationAreKeptConsistent() {
        var global = factory("{\"soundEvent\": \"dsurround:test\", \"global\": true, \"attenuation\": \"linear\"}");
        assertTrue(global.global());
        assertEquals(SoundInstance.Attenuation.NONE, global.attenuation(), "a global sound has no attenuation");

        var unattenuated = factory("{\"soundEvent\": \"dsurround:test\", \"attenuation\": \"none\"}");
        assertTrue(unattenuated.global(), "a sound without attenuation is global");
    }

    @Test
    void globalSoundPlaysAtTheListener() {
        // Regression: a global sound placed at world coordinates was made relative there, so it was heard as
        // coming from that far off in that direction
        var f = factory("{\"soundEvent\": \"dsurround:test\", \"global\": true}");

        var sound = f.createAtLocation(100D, 64D, 200D, 1F);

        assertTrue(sound.isRelative());
        assertEquals(0D, sound.getX());
        assertEquals(0D, sound.getY());
        assertEquals(0D, sound.getZ());
    }

    @Test
    void positionalSoundPlaysWhereItIsPut() {
        var f = factory("{\"soundEvent\": \"dsurround:test\"}");

        var sound = f.createAtLocation(100D, 64D, 200D, 1F);

        assertFalse(sound.isRelative());
        assertEquals(100D, sound.getX());
        assertEquals(64D, sound.getY());
        assertEquals(200D, sound.getZ());
    }

    @Test
    void musicFollowsEachFactorysSettings() {
        // Regression: music was cached by sound event alone, so the first settings asked for stuck, including
        // across reloads
        var short1 = factory("{\"soundEvent\": \"dsurround:tune\", \"music\": {\"min_delay\": 100, \"max_delay\": 200}}");
        var short2 = factory("{\"soundEvent\": \"dsurround:tune\", \"music\": {\"min_delay\": 100, \"max_delay\": 200}}");
        var longer = factory("{\"soundEvent\": \"dsurround:tune\", \"music\": {\"min_delay\": 5000, \"max_delay\": 9000}}");

        var a = short1.createAsMusic();
        var b = short2.createAsMusic();
        var c = longer.createAsMusic();

        assertSame(a, b, "the same event and settings share one instance");
        assertNotSame(a, c);
        assertEquals(100, a.getMinDelay());
        assertEquals(200, a.getMaxDelay());
        assertEquals(5000, c.getMinDelay());
        assertEquals(9000, c.getMaxDelay());
    }
}

package org.orecruncher.dsurround.processing.fog;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.renderer.fog.FogData;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.compat.FogCompat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the fog range results the calculators build, and the fog density codec.
 */
public class FogRangeTests {

    @Test
    void withRangeKeepsEverythingButTheEnvironmentalRange() {
        // Regression: the biome, morning and weather results were built without the rest of the fog's settings,
        // which fell back to the defaults
        var data = new FogData();
        data.environmentalStart = 100F;
        data.environmentalEnd = 200F;
        data.renderDistanceStart = 300F;
        data.renderDistanceEnd = 400F;
        data.skyEnd = 500F;
        data.cloudEnd = 600F;
        data.color = new Vector4f(0.1F, 0.2F, 0.3F, 1F);

        var result = VanillaFogRangeCalculator.withRange(data, 10F, 20F);

        assertNotSame(data, result);
        assertEquals(10F, result.environmentalStart);
        assertEquals(20F, result.environmentalEnd);
        assertEquals(300F, result.renderDistanceStart);
        assertEquals(400F, result.renderDistanceEnd);
        assertEquals(500F, result.skyEnd);
        assertEquals(600F, result.cloudEnd);
        assertEquals(data.color, result.color);
        assertNotSame(data.color, result.color, "the color is copied, not shared");
        assertEquals(100F, data.environmentalStart, "the input is left alone");
    }

    @Test
    void applyRangeSetsTheRangesTheCalculatorsWorkOut() {
        // The game's fog for the frame is changed in place; the render distance fog is the game's
        var target = new FogData();
        target.environmentalStart = 100F;
        target.environmentalEnd = 200F;
        target.skyEnd = 500F;
        target.cloudEnd = 600F;
        target.renderDistanceStart = 700F;
        var source = new FogData();
        source.environmentalStart = 10F;
        source.environmentalEnd = 20F;
        source.skyEnd = 1F;
        source.cloudEnd = 2F;
        source.renderDistanceStart = 3F;

        FogCompat.applyRange(target, source);

        assertEquals(10F, target.environmentalStart);
        assertEquals(20F, target.environmentalEnd);
        assertEquals(1F, target.skyEnd);
        assertEquals(2F, target.cloudEnd);
        assertEquals(700F, target.renderDistanceStart);
    }

    // The game's overworld fog in clear weather at 12 chunks
    private static FogData clearOverworld() {
        var data = new FogData();
        data.environmentalStart = 0F;
        data.environmentalEnd = 1024F;
        data.skyEnd = 192F;
        data.cloudEnd = 2048F;
        return data;
    }

    // The game's overworld fog in full rain: it moves the start behind the camera
    private static FogData rainingOverworld() {
        var data = clearOverworld();
        data.environmentalStart = -160F;
        data.environmentalEnd = 768F;
        return data;
    }

    @Test
    void noIntensityLeavesTheGameFogAlone() {
        var data = clearOverworld();

        assertSame(data, VanillaFogRangeCalculator.thicken(data, 192F, 0F, 0F));
    }

    @Test
    void fullEffectMatchesTheOldTerrainFog() {
        // Regression: scaling the game's 0..1024 range left the start at 0 and the end far beyond render distance,
        // so biome fog barely showed. In 1.21.1 intensity 0.47 at 12 chunks gave about 49..102.
        var result = VanillaFogRangeCalculator.thicken(clearOverworld(), 192F, 0.47F, 0F);

        assertEquals(192F * 0.53F, result.environmentalEnd, 0.01F);
        assertEquals(192F * 0.53F * 0.53F * VanillaFogRangeCalculator.CLEAR_ZONE, result.environmentalStart, 0.01F);
        assertEquals(result.environmentalEnd, result.skyEnd, 0.01F, "the sky fades with the terrain");
        assertEquals(result.environmentalEnd, result.cloudEnd, 0.01F, "the clouds fade with the terrain");
    }

    @Test
    void lowIntensityBlendsFromTheGameFog() {
        // Turning an effect on must not drop the fog end from 1024 to render distance in one step
        var result = VanillaFogRangeCalculator.thicken(clearOverworld(), 192F, 0.01F, 0F);

        assertTrue(result.environmentalEnd > 800F && result.environmentalEnd < 1024F, "end " + result.environmentalEnd);
        assertTrue(result.environmentalStart >= 0F && result.environmentalStart < 10F, "start " + result.environmentalStart);
    }

    @Test
    void fogThickensSteadilyWithIntensity() {
        for (var data : new FogData[]{clearOverworld(), rainingOverworld()}) {
            var previousEnd = data.environmentalEnd;
            for (var i = 1; i <= 100; i++) {
                var result = VanillaFogRangeCalculator.thicken(data, 192F, i / 100F, 0F);
                assertTrue(result.environmentalEnd <= previousEnd, "end rises at intensity " + i);
                assertTrue(result.environmentalStart <= result.environmentalEnd, "start beyond end at intensity " + i);
                assertTrue(result.environmentalEnd >= VanillaFogRangeCalculator.MIN_END, "end too near at intensity " + i);
                assertTrue(result.skyEnd <= data.skyEnd && result.cloudEnd <= data.cloudEnd, "sky or clouds thinned at intensity " + i);
                previousEnd = result.environmentalEnd;
            }
        }
    }

    @Test
    void nearerGameFogIsTheReference() {
        // The Nether's own fog ends at 96, so render distance isn't what the player sees
        var data = new FogData();
        data.environmentalStart = 10F;
        data.environmentalEnd = 96F;
        data.skyEnd = 96F;
        data.cloudEnd = 96F;

        var result = VanillaFogRangeCalculator.thicken(data, 192F, 0.5F, 0F);

        assertEquals(48F, result.environmentalEnd, 0.01F);
    }

    @Test
    void minimumStartIsKeptAtFullEffect() {
        var result = VanillaFogRangeCalculator.thicken(clearOverworld(), 192F, 0.8F, 11F);

        assertEquals(11F, result.environmentalStart, 0.01F);
        assertTrue(result.environmentalEnd > 11F);
    }

    @Test
    void fogDensityDecodesByName() {
        var result = FogDensity.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("heavy"));

        assertEquals(FogDensity.HEAVY, result.getOrThrow());
    }

    @Test
    void unknownFogDensityIsReportedAsSuch() {
        // Regression: the message said "unknown sound event type"
        var result = FogDensity.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("soup"));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().get().message().contains("unknown fog density"), result.error().get().message());
    }
}

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
    void applyRangeSetsOnlyTheEnvironmentalRange() {
        // The game's fog for the frame is changed in place; only the range the calculators work out is copied in
        var target = new FogData();
        target.environmentalStart = 100F;
        target.environmentalEnd = 200F;
        target.skyEnd = 500F;
        var source = new FogData();
        source.environmentalStart = 10F;
        source.environmentalEnd = 20F;
        source.skyEnd = 1F;

        FogCompat.applyRange(target, source);

        assertEquals(10F, target.environmentalStart);
        assertEquals(20F, target.environmentalEnd);
        assertEquals(500F, target.skyEnd);
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

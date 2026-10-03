package org.orecruncher.dsurround.processing.fog;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.renderer.FogRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the fog range results the calculators build, and the fog density codec.
 */
public class FogRangeTests {

    @Test
    void withRangeKeepsTheModeAndShape() {
        // Regression: the biome, morning and weather results were built without the shape, which fell back to the
        // default; only the holistic calculator copying it back hid that
        var data = new FogRenderer.FogData(FogRenderer.FogMode.FOG_TERRAIN);
        data.shape = FogShape.CYLINDER;
        data.start = 100F;
        data.end = 200F;

        var result = VanillaFogRangeCalculator.withRange(data, 10F, 20F);

        assertNotSame(data, result);
        assertEquals(FogRenderer.FogMode.FOG_TERRAIN, result.mode);
        assertEquals(FogShape.CYLINDER, result.shape);
        assertEquals(10F, result.start);
        assertEquals(20F, result.end);
        assertEquals(100F, data.start, "the input is left alone");
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

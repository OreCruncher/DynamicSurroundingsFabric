package org.orecruncher.dsurround.processing.fog;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.renderer.FogRenderer;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.precipitation.IColumnSource;

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

    // The game's terrain fog at 12 chunks
    private static FogRenderer.FogData terrain() {
        var data = new FogRenderer.FogData(FogRenderer.FogMode.FOG_TERRAIN);
        data.shape = FogShape.CYLINDER;
        data.start = 192F - 19.2F;
        data.end = 192F;
        return data;
    }

    // The game's sky fog at 12 chunks
    private static FogRenderer.FogData sky() {
        var data = new FogRenderer.FogData(FogRenderer.FogMode.FOG_SKY);
        data.shape = FogShape.CYLINDER;
        data.start = 0F;
        data.end = 192F;
        return data;
    }

    @Test
    void noIntensityLeavesTheGameFogAlone() {
        var data = terrain();

        assertSame(data, VanillaFogRangeCalculator.thicken(data, 0F, 0F));
    }

    @Test
    void thickeningScalesTheGameRange() {
        var result = VanillaFogRangeCalculator.thicken(terrain(), 0.5F, 0F);

        assertEquals(96F, result.end, 0.01F);
        assertEquals(172.8F * 0.25F, result.start, 0.01F);
        assertEquals(FogRenderer.FogMode.FOG_TERRAIN, result.mode);
        assertEquals(FogShape.CYLINDER, result.shape);
    }

    @Test
    void skyFogThickensToo() {
        // Regression: morning fog scaled the start, which is 0 for the sky, so it left the sky clear
        var result = VanillaFogRangeCalculator.thicken(sky(), 0.5F, 11F);

        assertEquals(96F, result.end, 0.01F);
        assertEquals(0F, result.start, "the minimum start never thins the game's fog");
    }

    @Test
    void minimumStartIsKept() {
        var result = VanillaFogRangeCalculator.thicken(terrain(), 0.8F, 11F);

        assertEquals(11F, result.start, 0.01F);
        assertEquals(192F * 0.2F, result.end, 0.01F);
    }

    @Test
    void fogThickensSteadilyWithIntensity() {
        for (var data : new FogRenderer.FogData[]{terrain(), sky()}) {
            var previousEnd = data.end;
            for (var i = 1; i <= 100; i++) {
                var result = VanillaFogRangeCalculator.thicken(data, i / 100F, 11F);
                assertTrue(result.end <= previousEnd, "end rises at intensity " + i);
                assertTrue(result.start <= result.end, "start beyond end at intensity " + i);
                assertTrue(result.start <= data.start || result.start <= 11F, "start moved out at intensity " + i);
                assertTrue(result.end >= VanillaFogRangeCalculator.MIN_END, "end too near at intensity " + i);
                previousEnd = result.end;
            }
        }
    }

    @Test
    void rainAddsFogOnlyUnderTheSky() {
        // Regression: rain thickened the fog in caves, buildings and deserts as much as out in the open
        assertEquals(1F, WeatherFogRangeCalculator.rainFogStrength(1F, 1F, true));
        assertEquals(0F, WeatherFogRangeCalculator.rainFogStrength(1F, 0F, true), "no fog away from the open sky");
        assertEquals(0.5F, WeatherFogRangeCalculator.rainFogStrength(1F, 0.5F, true), "as much as the player is out in it");
        assertEquals(0.5F, WeatherFogRangeCalculator.rainFogStrength(1F, 1F, false), "half in a biome that gets no rain");
        assertEquals(0.25F, WeatherFogRangeCalculator.rainFogStrength(0.5F, 1F, false));
        assertEquals(0F, WeatherFogRangeCalculator.rainFogStrength(0F, 1F, true));
    }

    @Test
    void dustStormsThickenTheFogUnderTheSky() {
        assertEquals(1F, WeatherFogRangeCalculator.dustFogStrength(1F, 1F));
        assertEquals(0.5F, WeatherFogRangeCalculator.dustFogStrength(0.5F, 1F));
        assertEquals(0F, WeatherFogRangeCalculator.dustFogStrength(1F, 0F), "no dust fog away from the open sky");
        assertEquals(0F, WeatherFogRangeCalculator.dustFogStrength(0F, 1F));
    }

    @Test
    void dustTurnsTheFogItsColourAtTheFogsOwnBrightness() {
        // A daytime blue-grey fog, turned all the way to sand: sand's hue, at the fog's brightness
        var rgb = new float[]{0.6F, 0.7F, 0.8F};
        WeatherFogRangeCalculator.tint(rgb, 0xF7E9A3, 1F);
        assertEquals(0.8F, Math.max(rgb[0], Math.max(rgb[1], rgb[2])), 1e-4F, "kept the fog's brightness");
        assertTrue(rgb[0] > rgb[1] && rgb[1] > rgb[2], "sandy: more red than green, more green than blue");

        // At night the fog is dark, and so is the dust in it
        var night = new float[]{0.02F, 0.02F, 0.05F};
        WeatherFogRangeCalculator.tint(night, 0xF7E9A3, 1F);
        assertTrue(Math.max(night[0], Math.max(night[1], night[2])) <= 0.05F + 1e-4F, "dust glowing at night");
    }

    @Test
    void theFogTakesTheColourOfTheDustAroundThePlayer() {
        // The puffs' colour, from the ground around, when dust is being drawn; the biome's otherwise
        assertEquals(0xD87F33, WeatherFogRangeCalculator.fogDustColor(0xD87F33, 0xF7E9A3));
        assertEquals(0xF7E9A3, WeatherFogRangeCalculator.fogDustColor(IColumnSource.NOT_A_DUST_SOURCE, 0xF7E9A3));
    }

    @Test
    void dustTintsTheFogPartWay() {
        var rgb = new float[]{0.6F, 0.7F, 0.8F};
        WeatherFogRangeCalculator.tint(rgb, 0xF7E9A3, 0F);
        assertArrayEquals(new float[]{0.6F, 0.7F, 0.8F}, rgb, "no dust, no change");

        var half = new float[]{0.6F, 0.7F, 0.8F};
        var full = new float[]{0.6F, 0.7F, 0.8F};
        WeatherFogRangeCalculator.tint(half, 0xF7E9A3, 0.5F);
        WeatherFogRangeCalculator.tint(full, 0xF7E9A3, 1F);
        for (int i = 0; i < 3; i++)
            assertEquals((0.6F + 0.1F * i + full[i]) / 2F, half[i], 1e-4F);
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

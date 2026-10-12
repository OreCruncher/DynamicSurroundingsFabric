package org.orecruncher.dsurround.effects.groundfog;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the ground fog's noise wraps without a seam: the shader's wrap and noise texture size match the mod's, and
 * the wrap is a whole number of times each of its noises repeats.
 */
public class GroundFogVolumeTests {

    private static String shader() throws IOException {
        try (var in = Objects.requireNonNull(GroundFogVolumeTests.class.getResourceAsStream(
                "/assets/dsurround/shaders/core/ground_fog_volume.fsh"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theShaderWrapsWhereTheModDoes() throws IOException {
        var match = Pattern.compile("const float WRAP = ([0-9.]+);").matcher(shader());
        assertTrue(match.find(), "the shader's WRAP");
        assertEquals(GroundFogVolume.NOISE_WRAP, Float.parseFloat(match.group(1)), 1e-6F);
    }

    @Test
    void theShaderRepeatsItsNoiseWhereTheModDoes() throws IOException {
        var match = Pattern.compile("const float NOISE_SIZE = ([0-9.]+);").matcher(shader());
        assertTrue(match.find(), "the shader's NOISE_SIZE");
        assertEquals(GroundFogVolume.NOISE_SIZE, Float.parseFloat(match.group(1)), 1e-6F);
    }

    @Test
    void theWrapIsAWholeNumberOfEachNoisesRepeats() throws IOException {
        // Regression: the noise's place in the world was wrapped where the noise didn't repeat, so it jumped there. Each
        // noise repeats every NOISE_SIZE of its cells, so the wrap must be a whole number of those
        var sizes = Pattern.compile("noise\\((?!vec2 p)[^;]*?/ ([0-9.]+)").matcher(shader());
        int found = 0;
        while (sizes.find()) {
            float size = Float.parseFloat(sizes.group(1));
            float repeats = GroundFogVolume.NOISE_WRAP / (size * GroundFogVolume.NOISE_SIZE);
            assertEquals(Math.round(repeats), repeats, 1e-4F, "noise of " + size + " blocks a cell repeats " + repeats + " times");
            found++;
        }
        assertEquals(5, found, "patches, the two swirls, wisps and tufts");
    }
}

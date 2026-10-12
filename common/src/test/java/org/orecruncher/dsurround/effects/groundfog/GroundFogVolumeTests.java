package org.orecruncher.dsurround.effects.groundfog;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the ground fog's noise wraps without a seam: the shader's wrap matches the mod's, and is a whole number of
 * cells of each of its noises.
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
    void theWrapIsAWholeNumberOfEachNoisesCells() throws IOException {
        // Regression: the noise's place in the world was wrapped where the noise didn't repeat, so it jumped there
        var sizes = Pattern.compile("noise\\([^;]*?/ ([0-9.]+)[^;]*?, WRAP / ([0-9.]+)\\)").matcher(shader());
        int found = 0;
        while (sizes.find()) {
            float size = Float.parseFloat(sizes.group(1));
            assertEquals(size, Float.parseFloat(sizes.group(2)), 1e-6F, "a noise wrapped by another's size");
            float cells = GroundFogVolume.NOISE_WRAP / size;
            assertEquals(Math.round(cells), cells, 1e-4F, "cells of " + size + " blocks: " + cells);
            found++;
        }
        assertEquals(4, found, "patches, the two swirls, and wisps");
    }
}

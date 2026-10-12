package org.orecruncher.dsurround.effects.aurora;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the aurora's noise doesn't repeat within a curtain: the shader's noise texture size matches the mod's,
 * and is wider than the finest noise across the longest curtain.
 */
public class AuroraRendererTests {

    // The renderer's shader takes its id from Constants, which needs the game's registries
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // From aurora.fsh: the finest noise's cells to a unit along a curtain, and the most its folds shift the rays
    private static final float FINEST = 23F;
    private static final float MOST_FOLD = 0.5F + 0.175F;

    private static String shader() throws IOException {
        try (var in = Objects.requireNonNull(AuroraRendererTests.class.getResourceAsStream(
                "/assets/dsurround/shaders/core/aurora.fsh"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theShaderRepeatsItsNoiseWhereTheModDoes() throws IOException {
        var match = Pattern.compile("const float NOISE_SIZE = ([0-9.]+);").matcher(shader());
        assertTrue(match.find(), "the shader's NOISE_SIZE");
        assertEquals(AuroraRenderer.NOISE_SIZE, Float.parseFloat(match.group(1)), 1e-6F);
    }

    @Test
    void theShadersFinestNoiseIsTheOneAllowedFor() throws IOException {
        assertTrue(shader().contains("rx * " + (int) FINEST + ".0"), "the rays' finest noise has changed");
    }

    @Test
    void theNoiseDoesNotRepeatWithinACurtain() {
        float longest = 0F;
        for (long night = 0; night < 2000; night++) {
            var aurora = Aurora.create(night, 3);
            for (int band = 0; band < aurora.bands(); band++)
                longest = Math.max(longest, Math.abs(aurora.textureU(band, 1F) - aurora.textureU(band, -1F)));
        }
        float cells = (longest + 2F * MOST_FOLD) * FINEST;
        assertTrue(cells < AuroraRenderer.NOISE_SIZE, cells + " cells across a curtain");
    }
}

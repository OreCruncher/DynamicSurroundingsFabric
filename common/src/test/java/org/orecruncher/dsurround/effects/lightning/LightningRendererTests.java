package org.orecruncher.dsurround.effects.lightning;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class LightningRendererTests {

    @Test
    void lightGoesInTheAlphaHalved() {
        assertEquals(0.5F, LightningRenderer.alpha(1F));
        assertEquals(1F, LightningRenderer.alpha(2F));
        assertEquals(1F, LightningRenderer.alpha(5F));
        assertEquals(0F, LightningRenderer.alpha(LightningRenderer.MIN_LIGHT / 2F));
    }

    @Test
    void theShadersConstantsMatch() throws IOException {
        assertEquals(1F / LightningRenderer.HALO, shaderConstant("CORE_WIDTH"), 1e-6F);
        // A patch's UV x runs a whole step either side of its cell, so the step must be over 2 to round back to it
        assertEquals(LightningRenderer.NOISE_CELL_STEP, shaderConstant("CELL_STEP"));
        assertTrue(LightningRenderer.NOISE_CELL_STEP > 2F);
        assertEquals(LightningRenderer.NOISE_SIZE, shaderConstant("NOISE_SIZE"));
    }

    private float shaderConstant(String name) throws IOException {
        String source;
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/assets/dsurround/shaders/core/lightning.fsh"))) {
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var matcher = Pattern.compile("const float " + name + " = ([0-9.]+);").matcher(source);
        assertTrue(matcher.find(), "no " + name + " in the shader");
        return Float.parseFloat(matcher.group(1));
    }
}

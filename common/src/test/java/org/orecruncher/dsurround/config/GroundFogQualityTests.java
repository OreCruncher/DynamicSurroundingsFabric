package org.orecruncher.dsurround.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the ground fog's quality settings are shown by vanilla's own names for its graphics settings, and draw
 * more finely as they go up.
 */
public class GroundFogQualityTests {

    @Test
    void eachIsNamedByAVanillaTranslation() throws IOException {
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/assets/minecraft/lang/en_us.json"));
             var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            var vanilla = JsonParser.parseReader(reader).getAsJsonObject();
            for (var quality : GroundFogQuality.values())
                assertTrue(vanilla.has(quality.translationKey()), quality + ": " + quality.translationKey());
        }
    }

    @Test
    void onlyNoneIsOff() {
        for (var quality : GroundFogQuality.values())
            assertEquals(quality != GroundFogQuality.NONE, quality.isEnabled(), quality.name());
    }

    @Test
    void eachDrawsMoreFinelyThanTheOneBelow() {
        // A smaller scale is more of the screen's pixels; the steps never fall
        var drawn = new GroundFogQuality[]{GroundFogQuality.FAST, GroundFogQuality.FANCY, GroundFogQuality.FABULOUS};
        for (int i = 1; i < drawn.length; i++) {
            assertTrue(drawn[i].scale() < drawn[i - 1].scale(), drawn[i] + " scale");
            assertTrue(drawn[i].maxSteps() >= drawn[i - 1].maxSteps(), drawn[i] + " steps");
        }
        for (var quality : drawn)
            assertTrue(quality.scale() >= 1 && quality.maxSteps() <= 24, quality.name());
    }
}

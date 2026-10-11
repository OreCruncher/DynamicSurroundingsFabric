package org.orecruncher.dsurround.effects.precipitation;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how storms sound: the calm rain, vanilla's heavier rain blended in, and the dust.
 */
public class StormSoundsTests {

    @Test
    void theCalmRainGrowsLouderWithTheStorm() {
        assertEquals(StormSounds.QUIETEST, StormSounds.calmShare(0F), 1e-6F);
        assertEquals(1F, StormSounds.calmShare(1F), 1e-6F);
        assertTrue(StormSounds.calmShare(0.3F) < StormSounds.calmShare(0.7F));
        // Outside 0 to 1, held to the ends
        assertEquals(1F, StormSounds.calmShare(1.3F), 1e-6F);
    }

    @Test
    void vanillasHeavyRainOnlyComesInForStrongerStorms() {
        assertEquals(0F, StormSounds.heavyShare(0.2F));
        assertEquals(0F, StormSounds.heavyShare(StormSounds.HEAVY_FROM));
        assertEquals(1F, StormSounds.heavyShare(StormSounds.HEAVY_FULL), 1e-6F);
        assertEquals(1F, StormSounds.heavyShare(1F), 1e-6F);
        float last = 0F;
        for (float level = StormSounds.HEAVY_FROM; level <= StormSounds.HEAVY_FULL; level += 0.05F) {
            var share = StormSounds.heavyShare(level);
            assertTrue(share >= last, "rises smoothly, at " + level);
            last = share;
        }
    }

    @Test
    void theDustFollowsTheStorm() {
        assertTrue(StormSounds.dustVolume(0.2F) < StormSounds.dustVolume(1F));
        assertEquals(StormSounds.DUST_VOLUME, StormSounds.dustVolume(1F), 1e-6F);
        assertEquals(1F - StormSounds.DUST_PITCH_RANGE, StormSounds.dustPitch(0F), 1e-6F);
        assertEquals(1F + StormSounds.DUST_PITCH_RANGE, StormSounds.dustPitch(1F), 1e-6F);
    }

    @Test
    void soundsAreTimedAsVanillasRain() {
        // Never on the tick after a sound; always once enough ticks have passed
        for (int roll = 0; roll < StormSounds.SOUND_CHANCE; roll++) {
            assertFalse(StormSounds.isTimeForSound(roll, 0));
            assertTrue(StormSounds.isTimeForSound(roll, StormSounds.SOUND_CHANCE));
        }
    }

    @Test
    void theSoundsAreDefinedAndTheirFilesShipped() throws Exception {
        try (var stream = Objects.requireNonNull(StormSoundsTests.class.getResourceAsStream("/assets/dsurround/sounds.json"))) {
            var sounds = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var location : new String[]{StormSounds.RAIN.getPath(), StormSounds.DUST.getPath()}) {
                assertTrue(sounds.has(location), location);
                for (var entry : sounds.getAsJsonObject(location).getAsJsonArray("sounds")) {
                    var name = entry.getAsString();
                    var file = "/assets/dsurround/sounds/" + name.substring(name.indexOf(':') + 1) + ".ogg";
                    assertNotNull(StormSoundsTests.class.getResource(file), file);
                }
            }
        }
    }
}

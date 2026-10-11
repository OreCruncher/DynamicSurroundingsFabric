package org.orecruncher.dsurround.runtime.weather;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.precipitation.Wind;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how loud the wind's ambient loops play for the wind there is.
 */
public class WindSoundsTests {

    private static float percent(int percent) {
        return Wind.speedOfPercent(percent);
    }

    @Test
    void aCalmStillSoundsWindy() {
        assertEquals(WindSounds.CALM_FLOOR, WindSounds.loopScale(0F, 1F), 1e-6F);
    }

    @Test
    void aFullStormsWindPlaysTheLoopAsItIs() {
        assertEquals(1F, WindSounds.loopScale(percent(100), 1F), 1e-6F);
    }

    @Test
    void theLoopRisesWithTheWind() {
        float last = 0F;
        for (int percent = 0; percent <= 300; percent += 10) {
            var scale = WindSounds.loopScale(percent(percent), 1F);
            assertTrue(scale >= last, "at " + percent + "%");
            last = scale;
        }
    }

    @Test
    void harderWindsAreLouderUpToALimit() {
        // A thunderstorm's wind, then a dust storm's, then far beyond
        assertTrue(WindSounds.loopScale(percent(130), 1F) > 1F);
        assertEquals(WindSounds.LOUDEST, WindSounds.loopScale(percent(200), 1F), 1e-6F);
        assertEquals(WindSounds.LOUDEST, WindSounds.loopScale(percent(300), 1F), 1e-6F);
    }

    @Test
    void underCoverItIsQuieter() {
        var open = WindSounds.loopScale(percent(100), 1F);
        var covered = WindSounds.loopScale(percent(100), 0F);
        assertEquals(open * WindSounds.SHELTERED, covered, 1e-6F);
        assertTrue(WindSounds.loopScale(percent(100), 0.5F) < open);
    }
}

package org.orecruncher.dsurround.effects.precipitation;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Configuration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the rain's color as the player tints it.
 */
public class RainColorTests {

    private static void assertSameColor(RainColor expected, RainColor actual) {
        assertEquals(expected.red(), actual.red(), 1e-5F, actual::toString);
        assertEquals(expected.green(), actual.green(), 1e-5F, actual::toString);
        assertEquals(expected.blue(), actual.blue(), 1e-5F, actual::toString);
    }

    @Test
    void theDefaultTintIsTheRainsLongStandingColor() {
        // The color rain was drawn in before it could be tinted: unchanged unless the player moves the slider
        var usual = RainColor.ofTint(RainColor.DEFAULT_TINT);
        assertEquals(0.72F, usual.red(), 1e-5F);
        assertEquals(0.78F, usual.green(), 1e-5F);
        assertEquals(0.88F, usual.blue(), 1e-5F);
        assertEquals(usual, RainColor.USUAL);
        assertEquals(RainColor.DEFAULT_TINT, new Configuration.WeatherOptions().rainTint, "the option's default");
    }

    @Test
    void noTintIsANearlyColorlessGray() {
        var neutral = RainColor.ofTint(0);
        float most = Math.max(neutral.red(), Math.max(neutral.green(), neutral.blue()));
        float least = Math.min(neutral.red(), Math.min(neutral.green(), neutral.blue()));
        assertTrue(most - least < 0.06F, () -> "not gray: " + neutral);
        assertTrue(least > 0.75F, () -> "not pale: " + neutral);
    }

    @Test
    void fullTintIsADeepBlue() {
        var blue = RainColor.ofTint(100);
        assertEquals(1F, blue.blue(), 1e-5F);
        assertTrue(blue.red() < 0.3F && blue.green() < 0.5F, () -> "not a deep blue: " + blue);
    }

    @Test
    void fromNearlyColorlessToADeepBlue() {
        assertSameColor(RainColor.NEUTRAL, RainColor.ofTint(0));
        assertSameColor(RainColor.BLUE, RainColor.ofTint(100));
        var previous = RainColor.ofTint(0);
        for (int tint = 10; tint <= 100; tint += 10) {
            var color = RainColor.ofTint(tint);
            assertTrue(color.blue() > previous.blue() && color.red() < previous.red(), "bluer as the tint rises");
            previous = color;
        }
    }

    @Test
    void outOfRangeTintsAreClamped() {
        assertEquals(RainColor.ofTint(0), RainColor.ofTint(-20));
        assertEquals(RainColor.ofTint(100), RainColor.ofTint(150));
    }

    @Test
    void everyChannelStaysInRange() {
        for (int tint = 0; tint <= 100; tint++) {
            var color = RainColor.ofTint(tint);
            for (float channel : new float[]{color.red(), color.green(), color.blue()})
                assertTrue(channel >= 0F && channel <= 1F);
        }
    }
}

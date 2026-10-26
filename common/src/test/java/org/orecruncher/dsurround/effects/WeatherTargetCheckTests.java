package org.orecruncher.dsurround.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how the weather target check names what was bound in the weather target's place.
 */
public class WeatherTargetCheckTests {

    @Test
    void namesTheLevelRenderersTargets() {
        assertEquals("the default frame buffer", WeatherTargetCheck.describe(0, 1, 2, 3, 4, 5));
        assertEquals("the main target", WeatherTargetCheck.describe(1, 1, 2, 3, 4, 5));
        assertEquals("the translucent target", WeatherTargetCheck.describe(2, 1, 2, 3, 4, 5));
        assertEquals("the item entity target", WeatherTargetCheck.describe(3, 1, 2, 3, 4, 5));
        assertEquals("the particles target", WeatherTargetCheck.describe(4, 1, 2, 3, 4, 5));
        assertEquals("the clouds target", WeatherTargetCheck.describe(5, 1, 2, 3, 4, 5));
    }

    @Test
    void anotherFrameBufferIsTakenForAnotherMods() {
        assertTrue(WeatherTargetCheck.describe(42, 1, 2, 3, 4, 5).contains("frame buffer 42"));
    }

    @Test
    void aMissingTargetMatchesNothing() {
        // A target the level renderer doesn't have is -1, which no frame buffer is
        assertEquals("the main target", WeatherTargetCheck.describe(1, 1, -1, -1, -1, -1));
    }
}

package org.orecruncher.dsurround.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how the target check names what was bound in the right target's place.
 */
public class TargetCheckTests {

    @Test
    void namesTheGamesTargets() {
        assertEquals("the default frame buffer", TargetCheck.describe(0, 1, 2, 3, 4, 5, 6));
        assertEquals("the main target", TargetCheck.describe(1, 1, 2, 3, 4, 5, 6));
        assertEquals("the translucent target", TargetCheck.describe(2, 1, 2, 3, 4, 5, 6));
        assertEquals("the item entity target", TargetCheck.describe(3, 1, 2, 3, 4, 5, 6));
        assertEquals("the particles target", TargetCheck.describe(4, 1, 2, 3, 4, 5, 6));
        assertEquals("the weather target", TargetCheck.describe(5, 1, 2, 3, 4, 5, 6));
        assertEquals("the clouds target", TargetCheck.describe(6, 1, 2, 3, 4, 5, 6));
    }

    @Test
    void anotherFrameBufferIsTakenForAnotherMods() {
        assertTrue(TargetCheck.describe(42, 1, 2, 3, 4, 5, 6).contains("frame buffer 42"));
    }

    @Test
    void aMissingTargetMatchesNothing() {
        // A target the game doesn't have is -1, which no frame buffer is
        assertEquals("the main target", TargetCheck.describe(1, 1, -1, -1, -1, -1, -1));
    }
}

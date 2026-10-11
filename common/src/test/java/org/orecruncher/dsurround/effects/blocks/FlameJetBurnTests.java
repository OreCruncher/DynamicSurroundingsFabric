package org.orecruncher.dsurround.effects.blocks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how hard a flame jet burns over its life.
 */
public class FlameJetBurnTests {

    @Test
    void flaresBurnsThenDiesDown() {
        int limit = 100;
        assertEquals(0F, FlameJetBurn.burn(0, limit), "nothing yet");
        assertEquals(0.5F, FlameJetBurn.burn(Math.round(limit * FlameJetBurn.FLARE / 2F), limit), 0.05F, "flaring up");
        assertEquals(1F, FlameJetBurn.burn(50, limit), "burning steadily");
        assertTrue(FlameJetBurn.burn(90, limit) < 1F, "dying down");
        assertEquals(0F, FlameJetBurn.burn(limit, limit), "out");
    }

    @Test
    void neverOutsideZeroToOne() {
        for (int age = -5; age <= 110; age++) {
            float burn = FlameJetBurn.burn(age, 100);
            assertTrue(burn >= 0F && burn <= 1F, "at " + age);
        }
        assertEquals(0F, FlameJetBurn.burn(5, 0), "no life to burn in");
    }
}

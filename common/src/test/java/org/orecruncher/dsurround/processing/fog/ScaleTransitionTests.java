package org.orecruncher.dsurround.processing.fog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the per-tick transition behind the biome fog fade.
 */
public class ScaleTransitionTests {

    private static final float STEP = 0.1F;
    private static final float EPSILON = 1e-5F;

    @Test
    void rendersDoNotMoveTheValue() {
        // Regression: the scale was stepped on every render frame, so the fade took a quarter of the time at 240 FPS
        // that it did at 60
        var transition = new ScaleTransition(STEP);
        transition.setTarget(1F);
        transition.tick();

        float first = transition.get(1F);
        for (int frame = 0; frame < 1000; frame++)
            transition.get(1F);

        assertEquals(first, transition.get(1F));
        assertEquals(STEP, first, EPSILON);
    }

    @Test
    void reachesTheTargetInAFixedNumberOfTicks() {
        var transition = new ScaleTransition(STEP);
        transition.setTarget(0.5F);

        for (int tick = 0; tick < 4; tick++)
            transition.tick();
        assertTrue(transition.get(1F) < 0.5F);

        transition.tick();
        assertEquals(0.5F, transition.get(1F), EPSILON);
    }

    @Test
    void doesNotOvershoot() {
        var transition = new ScaleTransition(STEP);
        transition.setTarget(0.25F);

        for (int tick = 0; tick < 10; tick++)
            transition.tick();

        assertEquals(0.25F, transition.get(1F), EPSILON);
        assertEquals(0.25F, transition.get(0F), EPSILON);
    }

    @Test
    void movesDownToALowerTarget() {
        var transition = new ScaleTransition(STEP);
        transition.setTarget(0.3F);
        for (int tick = 0; tick < 3; tick++)
            transition.tick();

        transition.setTarget(0F);
        transition.tick();

        assertEquals(0.2F, transition.get(1F), EPSILON);
    }

    @Test
    void interpolatesBetweenTicks() {
        var transition = new ScaleTransition(STEP);
        transition.setTarget(1F);
        transition.tick();
        transition.tick();

        assertEquals(0.1F, transition.get(0F), EPSILON);
        assertEquals(0.15F, transition.get(0.5F), EPSILON);
        assertEquals(0.2F, transition.get(1F), EPSILON);
    }

    @Test
    void resetJumpsToZero() {
        var transition = new ScaleTransition(STEP);
        transition.setTarget(1F);
        transition.tick();
        transition.tick();

        transition.reset();

        assertEquals(0F, transition.get(0F));
        assertEquals(0F, transition.get(1F));
        transition.tick();
        assertEquals(0F, transition.get(1F), "the target is cleared too");
    }
}

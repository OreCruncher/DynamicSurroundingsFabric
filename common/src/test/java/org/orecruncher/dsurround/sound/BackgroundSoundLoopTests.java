// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.orecruncher.dsurround.sound.BackgroundSoundLoop.nextScale;

class BackgroundSoundLoopTests {

    private static final float DELTA = 0.00001F;

    @Test
    void aQuietLoopFollowsASmallFallInItsTarget() {
        // A gust dying away: the step is bigger than the fall, and must not take the sound down with it
        assertEquals(0.034F, nextScale(0.035F, 0.034F, false), DELTA);
    }

    @Test
    void aQuietLoopIsNotSilencedByItsTargetFalling() {
        // At or under a step's size, a full step reached 0, which stopped the sound
        assertTrue(nextScale(0.03F, 0.029F, false) > 0F);
    }

    @Test
    void aLoopRisesAndFallsBySteps() {
        assertEquals(0.533F, nextScale(0.5F, 1F, false), DELTA);
        assertEquals(0.467F, nextScale(0.5F, 0.1F, false), DELTA);
    }

    @Test
    void aLoopRisesNoFurtherThanItsTarget() {
        assertEquals(0.035F, nextScale(0.02F, 0.035F, false), DELTA);
    }

    @Test
    void aLoopAtItsTargetStaysThere() {
        assertEquals(0.5F, nextScale(0.5F, 0.5F, false), DELTA);
    }

    @Test
    void fadingOutGoesAllTheWayToSilence() {
        assertEquals(0F, nextScale(0.02F, 0.5F, true), DELTA);
        assertEquals(0.467F, nextScale(0.5F, 1F, true), DELTA);
    }

    @Test
    void fadingOutIsHeldToALowerTarget() {
        assertEquals(0.1F, nextScale(0.5F, 0.1F, true), DELTA);
    }
}

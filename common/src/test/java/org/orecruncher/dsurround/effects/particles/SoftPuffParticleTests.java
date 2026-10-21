package org.orecruncher.dsurround.effects.particles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how a puff of mist or steam fades in and out over its life.
 */
public class SoftPuffParticleTests {

    @Test
    void fadesInQuicklyAndOutSlowly() {
        float fadeIn = 0.1F, fadeOut = 0.6F;
        assertEquals(0F, SoftPuffParticle.fade(0F, fadeIn, fadeOut), "invisible when new");
        assertEquals(0.5F, SoftPuffParticle.fade(0.05F, fadeIn, fadeOut), 1e-6F, "half way through fading in");
        assertEquals(1F, SoftPuffParticle.fade(fadeIn, fadeIn, fadeOut), 1e-6F, "whole once faded in");
        assertEquals(1F, SoftPuffParticle.fade(0.4F, fadeIn, fadeOut), 1e-6F, "whole before fading out");
        assertEquals(0.5F, SoftPuffParticle.fade(0.7F, fadeIn, fadeOut), 1e-5F, "half way through fading out");
        assertEquals(0F, SoftPuffParticle.fade(1F, fadeIn, fadeOut), "gone at the end");
    }

    @Test
    void neverOutsideZeroToOne() {
        for (float life = -0.5F; life <= 1.5F; life += 0.01F) {
            float fade = SoftPuffParticle.fade(life, 0.15F, 0.5F);
            assertTrue(fade >= 0F && fade <= 1F, "at " + life);
        }
    }
}

package org.orecruncher.dsurround.effects.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for when the player's jump breath is heard.
 */
public class PlayerJumpEffectTests {

    // A jump's first tick: vanilla's jump power, less a tick of gravity and drag
    private static final double TAKE_OFF = 0.33D;

    @Test
    void leavingTheGroundWithTheKeyDownIsAJump() {
        assertTrue(PlayerJumpEffect.jumped(false, true, true, TAKE_OFF));
    }

    @Test
    void onlyTheTickTheGroundIsLeft() {
        // Already in the air: the rest of the jump, or arriving in the world mid-air
        assertFalse(PlayerJumpEffect.jumped(true, true, true, TAKE_OFF));
        // Still on the ground: holding the key down between jumps
        assertFalse(PlayerJumpEffect.jumped(false, false, true, 0D));
    }

    @Test
    void walkingOffAnEdgeIsNoJump() {
        assertFalse(PlayerJumpEffect.jumped(false, true, false, -0.08D));
        // Even with the key down: falling, not rising
        assertFalse(PlayerJumpEffect.jumped(false, true, true, -0.08D));
    }

    @Test
    void steppingUpAStairIsNoJump() {
        // Lifted up a block with the key up
        assertFalse(PlayerJumpEffect.jumped(false, true, false, TAKE_OFF));
    }
}

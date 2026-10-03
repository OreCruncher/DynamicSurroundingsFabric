package org.orecruncher.dsurround.lib.math;

/**
 * Whether an entity is moving, from how far it actually moved in a tick. Movement input is only known for the
 * local player, and view bobbing lags behind and settles to zero in the air, so neither works for this.
 */
public final class Motion {

    // Below this (squared, per tick) the entity is standing still; it filters out interpolation jitter
    private static final double MOVING_THRESHOLD_SQ = 0.001D * 0.001D;

    private Motion() {
    }

    /**
     * Whether a move of {@code dx}, {@code dz} in one tick counts as moving horizontally.
     */
    public static boolean isMovingHorizontally(double dx, double dz) {
        return dx * dx + dz * dz > MOVING_THRESHOLD_SQ;
    }
}

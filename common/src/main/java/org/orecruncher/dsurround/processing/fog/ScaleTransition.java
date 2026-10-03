package org.orecruncher.dsurround.processing.fog;

import net.minecraft.util.Mth;

/**
 * A value that moves toward a target by a fixed amount each tick, and is interpolated between ticks when rendering.
 * Stepping on the tick rather than the frame keeps the transition the same length whatever the frame rate.
 * Client thread only.
 */
final class ScaleTransition {

    private final float stepPerTick;
    private float previous;
    private float current;
    private float target;

    ScaleTransition(float stepPerTick) {
        this.stepPerTick = stepPerTick;
    }

    void setTarget(float target) {
        this.target = target;
    }

    /**
     * Moves the value one step toward the target. Call once per client tick.
     */
    void tick() {
        this.previous = this.current;
        if (this.current < this.target)
            this.current = Math.min(this.current + this.stepPerTick, this.target);
        else if (this.current > this.target)
            this.current = Math.max(this.current - this.stepPerTick, this.target);
    }

    /**
     * The value between the last two ticks, {@code partialTick} of the way from the earlier to the later.
     */
    float get(float partialTick) {
        return Mth.lerp(partialTick, this.previous, this.current);
    }

    /**
     * Jumps straight to zero, with no transition.
     */
    void reset() {
        this.previous = this.current = this.target = 0F;
    }
}

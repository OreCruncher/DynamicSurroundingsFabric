// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.footprints;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.TrackedGround;

/**
 * What an entity's feet are carrying, and how many steps of it are left. Each step spends one, whatever it lands on;
 * walking through more of something starts over with that.
 */
final class CarriedGround {

    // How strong the last print is, next to the first
    static final float LAST_STRENGTH = 0.35F;

    private @Nullable TrackedGround ground;
    private int steps;

    void pickUp(TrackedGround ground) {
        this.ground = ground;
        this.steps = ground.steps();
    }

    void clear() {
        this.ground = null;
        this.steps = 0;
    }

    /**
     * What is carried, null if nothing is
     */
    @Nullable TrackedGround ground() {
        return this.ground;
    }

    /**
     * Spends a step.
     *
     * @return how strong this step's print is, 1 for the first and {@link #LAST_STRENGTH} for the last; 0 if nothing is
     * carried
     */
    float spend() {
        if (this.ground == null)
            return 0F;
        float strength = strength(this.steps, this.ground.steps());
        if (--this.steps <= 0)
            this.ground = null;
        return strength;
    }

    /**
     * How strong a print is with this many steps left, this one included, of this many
     */
    static float strength(int left, int total) {
        if (total <= 1)
            return 1F;
        return LAST_STRENGTH + (1F - LAST_STRENGTH) * (left - 1) / (total - 1);
    }
}

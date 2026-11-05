// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

/**
 * What feet carry off soft ground (or out of water) and track onto hard ground: the color of the prints, how many steps
 * it lasts, how long a print of it stays, in ticks, and whether its prints also show on soft ground (snow and mud do, in
 * place of the print pressed there, until it is used up; the rest only on hard ground).
 */
public enum TrackedGround {
    SNOW(0xF4F8FF, 6, 300F, true),
    MUD(0x3D2B1F, 10, 600F, true),
    SAND(0xD9C58F, 6, 400F, false),
    RED_SAND(0xB5622A, 6, 400F, false),
    SOUL_SAND(0x3E2F27, 8, 400F, false),
    // A dark, damp print that dries quickly
    WATER(0x1F2A33, 6, 120F, false);

    private final int color;
    private final int steps;
    private final float life;
    private final boolean showsOnSoftGround;

    TrackedGround(int color, int steps, float life, boolean showsOnSoftGround) {
        this.color = color;
        this.steps = steps;
        this.life = life;
        this.showsOnSoftGround = showsOnSoftGround;
    }

    /**
     * The color of its prints, as 0xRRGGBB
     */
    public int color() {
        return this.color;
    }

    /**
     * How many steps feet carry it
     */
    public int steps() {
        return this.steps;
    }

    /**
     * How long a print of it lasts, in ticks
     */
    public float life() {
        return this.life;
    }

    /**
     * Whether its prints show on soft ground too
     */
    public boolean showsOnSoftGround() {
        return this.showsOnSoftGround;
    }
}

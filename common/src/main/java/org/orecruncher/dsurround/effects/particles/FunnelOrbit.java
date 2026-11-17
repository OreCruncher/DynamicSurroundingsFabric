// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import net.minecraft.util.Mth;
import org.orecruncher.dsurround.effects.actors.VortexPath;

/**
 * Where a particle whirled in a vortex is: a height above the vortex's foot, an angle around its axis and a radius from
 * it, the axis bending and swaying as the vortex goes on. Each particle in a vortex holds its own, climbing and going
 * around a tick at a time, and is moved to where it says: around the funnel, wherever the vortex has got to. One
 * particle's only, so it is safe on whatever thread ticks the particle.
 */
final class FunnelOrbit {

    private final VortexPath path;
    // The vortex's age when the particle was made, and the height of its foot then
    private final int vortexAge;
    private final double footY;
    private float height;
    private float angle;
    private float radius;

    FunnelOrbit(VortexPath path, int vortexAge, double footY, float height, float angle, float radius) {
        this.path = path;
        this.vortexAge = vortexAge;
        this.footY = footY;
        this.height = height;
        this.angle = angle;
        this.radius = radius;
    }

    VortexPath path() {
        return this.path;
    }

    float height() {
        return this.height;
    }

    float radius() {
        return this.radius;
    }

    /**
     * Climbs, in blocks
     */
    void climb(float rise) {
        this.height += rise;
    }

    /**
     * Goes around a tick's worth, at a radius
     */
    void around(float radius) {
        this.radius = radius;
        this.angle += this.path.spin(radius);
    }

    /**
     * The way it is going around, as an angle: the way it is flung when it leaves the funnel
     */
    float tangent() {
        return this.path.tangent(this.angle, this.radius);
    }

    /**
     * Where it is, the particle this many ticks old: around the funnel's axis at its height, which bends and sways
     */
    double x(int particleAge) {
        return this.path.axisX(this.height, this.vortexAge + particleAge) + Mth.cos(this.angle) * this.radius;
    }

    double y() {
        return this.footY + this.height;
    }

    double z(int particleAge) {
        return this.path.axisZ(this.height, this.vortexAge + particleAge) + Mth.sin(this.angle) * this.radius;
    }
}

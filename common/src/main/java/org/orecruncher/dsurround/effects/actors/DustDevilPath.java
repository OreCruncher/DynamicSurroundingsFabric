// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.util.Mth;
import org.orecruncher.dsurround.lib.random.Randomizer;

/**
 * A dust devil's whole life worked out from where it starts, its seed and the day's wind, so a player arriving part
 * way through sees it where everyone else does: it drifts downwind, weaving from side to side, grows to its strength
 * and dies away again, and its funnel is narrow at the ground and opens out above. The funnel bends: higher up, it is
 * where the foot was a little while ago, so it curves after the foot as it weaves, and it sways more the higher it is. Doesn't change once made, so its
 * particles, which may tick on other threads, follow it through it.
 */
public final class DustDevilPath {

    // How long one lasts, in ticks, and how long it takes to grow and to die away
    static final int MIN_LIFETIME = 750;
    public static final int MAX_LIFETIME = 1650;
    static final int FADE_TICKS = 200;
    // How fast it travels, in blocks a tick, and how far its way can be off the wind's, in radians
    private static final double MIN_SPEED = 0.03D;
    private static final double MAX_SPEED = 0.08D;
    private static final float OFF_WIND = 0.6F;
    // How far it weaves either side of its way, in blocks, and how long a weave takes, in ticks
    private static final double MIN_WEAVE = 2D;
    private static final double MAX_WEAVE = 7D;
    private static final float MIN_WEAVE_TICKS = 200F;
    private static final float MAX_WEAVE_TICKS = 420F;
    // How tall it stands, in blocks, and its funnel's radius at the ground and, as a share of its height, at the top
    static final float MIN_HEIGHT = 9F;
    static final float MAX_HEIGHT = 18F;
    static final float BASE_RADIUS = 0.6F;
    private static final float TOP_RADIUS_SHARE = 0.2F;
    // How the funnel opens out going up: a power of the height, over 1 so it stays narrow low down
    private static final float FLARE = 1.6F;
    // How fast the dust goes around, in blocks a tick, the same at every height, so it turns quickest low down
    private static final float SPIN_SPEED = 0.45F;
    // How far behind the foot the top of the funnel is, in ticks of its travel: a power of the height, so it curves
    private static final float TRAIL_TICKS = 70F;
    private static final float TRAIL_BEND = 1.4F;
    // How far the top sways either way, in blocks, how long a sway takes, in ticks, and how far it has climbed the
    // funnel by the time it reaches the top, in radians, so it runs up it as a wave
    private static final float SWAY = 1.6F;
    private static final float MIN_SWAY_TICKS = 90F;
    private static final float MAX_SWAY_TICKS = 160F;
    private static final float SWAY_CLIMB = 2.5F;
    // How much its strength wavers, and how fast
    private static final float WAVER = 0.15F;
    private static final float WAVER_RATE = Mth.TWO_PI / 90F;

    private final double startX;
    private final double startZ;
    private final double wayX;
    private final double wayZ;
    private final double speed;
    private final double weave;
    private final float weaveRate;
    private final float weavePhase;
    private final float waverPhase;
    private final float swayRateX;
    private final float swayRateZ;
    private final float swayPhaseX;
    private final float swayPhaseZ;
    private final int lifetime;
    private final float height;
    private final float topRadius;
    // Spin one way or the other: real ones go either way
    private final float spin;

    /**
     * @param windHeading the way the day's wind blows, in radians from east toward south
     */
    public static DustDevilPath of(double x, double z, long seed, float windHeading) {
        return new DustDevilPath(x, z, seed, windHeading);
    }

    private DustDevilPath(double x, double z, long seed, float windHeading) {
        var random = Randomizer.create(seed);
        this.startX = x;
        this.startZ = z;
        var heading = windHeading + (random.nextFloat() * 2F - 1F) * OFF_WIND;
        this.wayX = Mth.cos(heading);
        this.wayZ = Mth.sin(heading);
        this.speed = Mth.lerp(random.nextDouble(), MIN_SPEED, MAX_SPEED);
        this.weave = Mth.lerp(random.nextDouble(), MIN_WEAVE, MAX_WEAVE);
        this.weaveRate = Mth.TWO_PI / Mth.lerp(random.nextFloat(), MIN_WEAVE_TICKS, MAX_WEAVE_TICKS);
        this.weavePhase = random.nextFloat() * Mth.TWO_PI;
        this.waverPhase = random.nextFloat() * Mth.TWO_PI;
        this.lifetime = MIN_LIFETIME + random.nextInt(MAX_LIFETIME - MIN_LIFETIME + 1);
        this.height = Mth.lerp(random.nextFloat(), MIN_HEIGHT, MAX_HEIGHT);
        this.topRadius = this.height * TOP_RADIUS_SHARE;
        this.spin = random.nextBoolean() ? 1F : -1F;
        this.swayRateX = Mth.TWO_PI / Mth.lerp(random.nextFloat(), MIN_SWAY_TICKS, MAX_SWAY_TICKS);
        this.swayRateZ = Mth.TWO_PI / Mth.lerp(random.nextFloat(), MIN_SWAY_TICKS, MAX_SWAY_TICKS);
        this.swayPhaseX = random.nextFloat() * Mth.TWO_PI;
        this.swayPhaseZ = random.nextFloat() * Mth.TWO_PI;
    }

    public int lifetime() {
        return this.lifetime;
    }

    public float height() {
        return this.height;
    }

    /**
     * Where it is at an age, in ticks
     */
    public double x(float age) {
        return this.startX + this.wayX * this.speed * age - this.wayZ * this.weaveAt(age);
    }

    public double z(float age) {
        return this.startZ + this.wayZ * this.speed * age + this.wayX * this.weaveAt(age);
    }

    /**
     * Where the funnel's axis is at a height above the ground, at an age: the foot at the ground, then trailing it and
     * swaying more going up
     */
    public double axisX(float h, float age) {
        var up = this.up(h);
        return this.x(age - this.trail(up)) + SWAY * up * up * Mth.sin(age * this.swayRateX - up * SWAY_CLIMB + this.swayPhaseX);
    }

    public double axisZ(float h, float age) {
        var up = this.up(h);
        return this.z(age - this.trail(up)) + SWAY * up * up * Mth.sin(age * this.swayRateZ - up * SWAY_CLIMB + this.swayPhaseZ);
    }

    // How far up the funnel a height is, 0 to 1
    private float up(float h) {
        return Mth.clamp(h / this.height, 0F, 1F);
    }

    private float trail(float up) {
        return TRAIL_TICKS * (float) Math.pow(up, TRAIL_BEND);
    }

    private double weaveAt(float age) {
        // From the middle of its way, so it starts on it
        return this.weave * (Mth.sin(age * this.weaveRate + this.weavePhase) - Mth.sin(this.weavePhase));
    }

    /**
     * How strong it is at an age, 0 to 1: growing, wavering, then dying away; 0 before and after its life
     */
    public float strength(float age) {
        if (age <= 0F || age >= this.lifetime)
            return 0F;
        var fade = Math.min(1F, Math.min(age, this.lifetime - age) / FADE_TICKS);
        return fade * (1F - WAVER + WAVER * Mth.sin(age * WAVER_RATE + this.waverPhase));
    }

    /**
     * The funnel's radius at a height above the ground, in blocks
     */
    public float radius(float h) {
        var up = this.up(h);
        return BASE_RADIUS + (this.topRadius - BASE_RADIUS) * (float) Math.pow(up, FLARE);
    }

    /**
     * How far the dust at a radius goes around in a tick, in radians, signed by the way it spins
     */
    public float spin(float radius) {
        return this.spin * SPIN_SPEED / Math.max(radius, BASE_RADIUS);
    }
}

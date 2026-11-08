// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.util.Mth;
import org.orecruncher.dsurround.lib.random.Randomizer;

/**
 * A whirling actor's whole life (a dust devil's, a waterspout's) worked out from where it starts, its seed and the
 * day's wind, so a player arriving part way through sees it where everyone else does: it drifts downwind, weaving from
 * side to side, grows to its strength and dies away again, and its funnel is narrow at the foot and opens out above.
 * The funnel bends: higher up, it is where the foot was a little while ago, so it curves after the foot as it weaves,
 * and it sways more the higher it is. How big, fast and long-lived each kind is comes from its {@link Shape}. Doesn't
 * change once made, so its particles, which may tick on other threads, follow it through it.
 */
public final class VortexPath {

    /**
     * What a kind of vortex is like
     *
     * @param minLifetime  how long one lasts, in ticks, at least
     * @param maxLifetime  and at most
     * @param fadeTicks    how long it takes to grow and to die away
     * @param minSpeed     how fast it travels, in blocks a tick, at least
     * @param maxSpeed     and at most
     * @param offWind      how far its way can be off the wind's, in radians
     * @param minWeave     how far it weaves either side of its way, in blocks, at least
     * @param maxWeave     and at most
     * @param minHeight    how tall it stands, in blocks, at least, unless it is given its height
     * @param maxHeight    and at most
     * @param baseRadius   its funnel's radius at the foot, in blocks
     * @param topShare     its funnel's radius at the top, as a share of its height
     * @param flare        how the funnel opens out going up: a power of the height, over 1 so it stays narrow low down
     * @param spinSpeed    how fast it goes around, in blocks a tick, the same at every height, so quickest low down
     * @param trailTicks   how far behind the foot the top is, in ticks of its travel
     * @param trailBend    a power of the height for the trail, over 1 so it curves
     * @param sway         how far the top sways either way, in blocks
     */
    public record Shape(int minLifetime, int maxLifetime, int fadeTicks, double minSpeed, double maxSpeed, float offWind,
                        double minWeave, double maxWeave, float minHeight, float maxHeight, float baseRadius,
                        float topShare, float flare, float spinSpeed, float trailTicks, float trailBend, float sway) {
    }

    public static final Shape DUST_DEVIL = new Shape(750, 1650, 200, 0.03D, 0.08D, 0.6F, 2D, 7D, 9F, 18F, 0.6F,
            0.2F, 1.6F, 0.45F, 70F, 1.4F, 1.6F);
    // A dust devil's size, a little shorter, and quicker, as snow is lifted by a sharper wind
    public static final Shape SNOWSPOUT = new Shape(600, 1400, 160, 0.04D, 0.10D, 0.5F, 2D, 6D, 7F, 15F, 0.6F,
            0.22F, 1.6F, 0.5F, 60F, 1.4F, 1.4F);
    // A column of fire over lava: taller than a dust devil, slower, spinning hard, and long lived
    public static final Shape FIRESPOUT = new Shape(800, 1800, 200, 0.02D, 0.05D, 3.14F, 2D, 6D, 10F, 22F, 0.8F,
            0.15F, 1.5F, 0.6F, 60F, 1.4F, 1.5F);
    // Tall and thin, reaching up to the clouds, its top far behind its foot and swaying wide; slower, and longer lived
    public static final Shape WATERSPOUT = new Shape(900, 2400, 300, 0.02D, 0.06D, 0.4F, 3D, 10D, 60F, 130F, 2F,
            0.06F, 2.5F, 0.6F, 400F, 1.2F, 6F);

    // How long a weave takes, in ticks
    private static final float MIN_WEAVE_TICKS = 200F;
    private static final float MAX_WEAVE_TICKS = 420F;
    // How long a sway takes, in ticks, and how far it has climbed the funnel by the time it reaches the top, in
    // radians, so it runs up it as a wave
    private static final float MIN_SWAY_TICKS = 90F;
    private static final float MAX_SWAY_TICKS = 160F;
    private static final float SWAY_CLIMB = 2.5F;
    // How much its strength wavers, and how fast
    private static final float WAVER = 0.15F;
    private static final float WAVER_RATE = Mth.TWO_PI / 90F;

    private final Shape shape;
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
    public static VortexPath of(Shape shape, double x, double z, long seed, float windHeading) {
        return new VortexPath(shape, x, z, seed, windHeading, Float.NaN);
    }

    /**
     * One of a given height, as a waterspout reaching up to the clouds
     */
    public static VortexPath of(Shape shape, double x, double z, long seed, float windHeading, float height) {
        return new VortexPath(shape, x, z, seed, windHeading, height);
    }

    private VortexPath(Shape shape, double x, double z, long seed, float windHeading, float height) {
        var random = Randomizer.create(seed);
        this.shape = shape;
        this.startX = x;
        this.startZ = z;
        var heading = windHeading + (random.nextFloat() * 2F - 1F) * shape.offWind();
        this.wayX = Mth.cos(heading);
        this.wayZ = Mth.sin(heading);
        this.speed = Mth.lerp(random.nextDouble(), shape.minSpeed(), shape.maxSpeed());
        this.weave = Mth.lerp(random.nextDouble(), shape.minWeave(), shape.maxWeave());
        this.weaveRate = Mth.TWO_PI / Mth.lerp(random.nextFloat(), MIN_WEAVE_TICKS, MAX_WEAVE_TICKS);
        this.weavePhase = random.nextFloat() * Mth.TWO_PI;
        this.waverPhase = random.nextFloat() * Mth.TWO_PI;
        this.lifetime = shape.minLifetime() + random.nextInt(shape.maxLifetime() - shape.minLifetime() + 1);
        var drawnHeight = Mth.lerp(random.nextFloat(), shape.minHeight(), shape.maxHeight());
        this.height = Float.isNaN(height) ? drawnHeight : Math.max(1F, height);
        this.topRadius = Math.max(shape.baseRadius(), this.height * shape.topShare());
        this.spin = random.nextBoolean() ? 1F : -1F;
        this.swayRateX = Mth.TWO_PI / Mth.lerp(random.nextFloat(), MIN_SWAY_TICKS, MAX_SWAY_TICKS);
        this.swayRateZ = Mth.TWO_PI / Mth.lerp(random.nextFloat(), MIN_SWAY_TICKS, MAX_SWAY_TICKS);
        this.swayPhaseX = random.nextFloat() * Mth.TWO_PI;
        this.swayPhaseZ = random.nextFloat() * Mth.TWO_PI;
    }

    public Shape shape() {
        return this.shape;
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
     * Where the funnel's axis is at a height above the foot, at an age: the foot at the bottom, then trailing it and
     * swaying more going up
     */
    public double axisX(float h, float age) {
        var up = this.up(h);
        return this.x(age - this.trail(up)) + this.shape.sway() * up * up * Mth.sin(age * this.swayRateX - up * SWAY_CLIMB + this.swayPhaseX);
    }

    public double axisZ(float h, float age) {
        var up = this.up(h);
        return this.z(age - this.trail(up)) + this.shape.sway() * up * up * Mth.sin(age * this.swayRateZ - up * SWAY_CLIMB + this.swayPhaseZ);
    }

    // How far up the funnel a height is, 0 to 1
    private float up(float h) {
        return Mth.clamp(h / this.height, 0F, 1F);
    }

    private float trail(float up) {
        return this.shape.trailTicks() * (float) Math.pow(up, this.shape.trailBend());
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
        var fade = Math.min(1F, Math.min(age, this.lifetime - age) / this.shape.fadeTicks());
        return fade * (1F - WAVER + WAVER * Mth.sin(age * WAVER_RATE + this.waverPhase));
    }

    /**
     * The funnel's radius at a height above the foot, in blocks
     */
    public float radius(float h) {
        var up = this.up(h);
        return this.shape.baseRadius() + (this.topRadius - this.shape.baseRadius()) * (float) Math.pow(up, this.shape.flare());
    }

    /**
     * How far what is at a radius goes around in a tick, in radians, signed by the way it spins
     */
    public float spin(float radius) {
        return this.spin * this.shape.spinSpeed() / Math.max(radius, this.shape.baseRadius());
    }
}

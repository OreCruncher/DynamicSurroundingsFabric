// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.util.Mth;
import org.orecruncher.dsurround.lib.random.Randomizer;

/**
 * An apparition's life, worked out from where it starts and its seed: how long it lingers, the few haunts it keeps to
 * near where it started, and when it moves on from one to the next, so every player finds it at the same haunt. How it
 * gets from one to the next is found as it goes, around what is in the way ({@link ApparitionRoute}), so that part is
 * each player's own; where it is at a haunt isn't.
 */
public final class ApparitionPath implements ActorPath {

    // How long one lingers, in ticks, and how long it takes to show at the start of its life and to fade at the end
    static final int MIN_LIFETIME = 2400;
    public static final int MAX_LIFETIME = 6000;
    static final float FADE_TICKS = 80F;
    // Its haunts: the first where it started, the others this far from it, in blocks
    static final int HAUNTS = 4;
    private static final float MIN_HAUNT_DISTANCE = 8F;
    private static final float MAX_HAUNT_DISTANCE = 18F;
    // How long it stays with a haunt, in ticks, its way there included
    private static final int MIN_HAUNT_TICKS = 500;
    private static final int MAX_HAUNT_TICKS = 900;
    // How far it bobs as it floats, in blocks, and how long a bob takes, in ticks
    static final float BOB = 0.07F;
    private static final float MIN_BOB_TICKS = 70F;
    private static final float MAX_BOB_TICKS = 110F;

    private final double[] hauntX = new double[HAUNTS];
    private final double[] hauntZ = new double[HAUNTS];
    private final int lifetime;
    private final int hauntTicks;
    private final float bobRate;
    private final float bobPhase;
    private final long seed;

    public static ApparitionPath of(double x, double z, long seed) {
        return new ApparitionPath(x, z, seed);
    }

    private ApparitionPath(double x, double z, long seed) {
        this.seed = seed;
        var random = Randomizer.create(seed);
        this.lifetime = MIN_LIFETIME + random.nextInt(MAX_LIFETIME - MIN_LIFETIME + 1);
        this.hauntTicks = MIN_HAUNT_TICKS + random.nextInt(MAX_HAUNT_TICKS - MIN_HAUNT_TICKS + 1);
        this.hauntX[0] = x;
        this.hauntZ[0] = z;
        // Around where it started, a quarter turn or so apart, so the next is never beside the last
        var turn = random.nextFloat() * Mth.TWO_PI;
        for (int i = 1; i < HAUNTS; i++) {
            turn += Mth.HALF_PI + (random.nextFloat() - 0.5F);
            var distance = Mth.lerp(random.nextFloat(), MIN_HAUNT_DISTANCE, MAX_HAUNT_DISTANCE);
            this.hauntX[i] = x + Mth.cos(turn) * distance;
            this.hauntZ[i] = z + Mth.sin(turn) * distance;
        }
        this.bobRate = Mth.TWO_PI / Mth.lerp(random.nextFloat(), MIN_BOB_TICKS, MAX_BOB_TICKS);
        this.bobPhase = random.nextFloat() * Mth.TWO_PI;
    }

    @Override
    public int lifetime() {
        return this.lifetime;
    }

    /**
     * Where it is at an age: at the haunt it is with then
     */
    @Override
    public double x(float age) {
        return this.hauntX[this.hauntAt(age)];
    }

    @Override
    public double z(float age) {
        return this.hauntZ[this.hauntAt(age)];
    }

    /**
     * The haunt it is with, at an age
     */
    public int hauntAt(float age) {
        return Math.floorMod((int) (age / this.hauntTicks), HAUNTS);
    }

    public double hauntX(int haunt) {
        return this.hauntX[haunt];
    }

    public double hauntZ(int haunt) {
        return this.hauntZ[haunt];
    }

    /**
     * How much it shows at an age, 0 to 1: showing at the start of its life and fading at the end
     */
    public float presence(float age) {
        if (age <= 0F || age >= this.lifetime)
            return 0F;
        return Math.min(1F, Math.min(age, this.lifetime - age) / FADE_TICKS);
    }

    /**
     * How far above its footing it floats at an age, in blocks
     */
    public float bob(float age) {
        return BOB * (1F + Mth.sin(age * this.bobRate + this.bobPhase));
    }

    /**
     * Its seed, for what it does that isn't shared (where it glances)
     */
    public long seed() {
        return this.seed;
    }
}

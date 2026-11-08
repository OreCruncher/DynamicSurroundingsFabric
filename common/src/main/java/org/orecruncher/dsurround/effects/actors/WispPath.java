// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.util.Mth;
import org.orecruncher.dsurround.lib.random.Mixers;
import org.orecruncher.dsurround.lib.random.Randomizer;

/**
 * A wisp's whole life, worked out from where it starts and its seed, so every player sees the same lights in the same
 * places. It doesn't travel as a whirl does: it keeps to a few haunts near where it started, staying at one a while,
 * then going out and showing again at the next. At a haunt its lights drift together in slow loops, each in a little
 * loop of its own, bobbing; now and then one goes out and shows again a little way off, so it is never clear how many
 * there are. Doesn't change once made, so its lights, which may tick on other threads, can follow it.
 */
public final class WispPath implements ActorPath {

    // How long one lasts, in ticks, and how long it takes to show and to go out at the start and end of its life
    static final int MIN_LIFETIME = 1200;
    public static final int MAX_LIFETIME = 3600;
    private static final int FADE_TICKS = 100;
    // Its lights, and its haunts: the first where it started, the others this far from it, in blocks
    private static final int MIN_LIGHTS = 1;
    private static final int MAX_LIGHTS = 4;
    static final int HAUNTS = 4;
    private static final float MIN_HAUNT_DISTANCE = 12F;
    private static final float MAX_HAUNT_DISTANCE = 24F;
    // How long it stays at a haunt, in ticks, and how long it takes to go out before it moves and show after
    private static final int MIN_HAUNT_TICKS = 600;
    private static final int MAX_HAUNT_TICKS = 1200;
    private static final float HAUNT_FADE = 30F;
    // The group's loop around its haunt, in blocks, and how long it takes, in ticks
    private static final float GROUP_LOOP = 4F;
    private static final float MIN_GROUP_TICKS = 500F;
    private static final float MAX_GROUP_TICKS = 900F;
    // Each light's own loop around the group, in blocks and ticks
    private static final float MIN_LOOP = 0.8F;
    private static final float MAX_LOOP = 3F;
    private static final float MIN_LOOP_TICKS = 120F;
    private static final float MAX_LOOP_TICKS = 260F;
    // How high each floats over what is under it, in blocks, how far it bobs, and how long a bob takes, in ticks
    private static final float MIN_LIFT = 0.6F;
    private static final float MAX_LIFT = 1.8F;
    private static final float BOB = 0.3F;
    private static final float MIN_BOB_TICKS = 50F;
    private static final float MAX_BOB_TICKS = 90F;
    // How often each light goes out, in ticks, for how long, and how long it takes to go out and to show again
    private static final int MIN_BLINK_TICKS = 160;
    private static final int MAX_BLINK_TICKS = 300;
    private static final int DARK_TICKS = 30;
    private static final float BLINK_FADE = 10F;
    // How much a light's brightness wavers, and how fast
    private static final float WAVER = 0.15F;
    private static final float WAVER_RATE = Mth.TWO_PI / 37F;

    private record Light(float loop, float loopRate, float loopPhase, float lift, float bobRate, float bobPhase,
                         int blinkTicks, int blinkPhase, long blinkSeed) {
    }

    private final double[] hauntX = new double[HAUNTS];
    private final double[] hauntZ = new double[HAUNTS];
    private final int lifetime;
    private final int hauntTicks;
    private final float groupRate;
    private final float groupPhase;
    private final Light[] lights;

    public static WispPath of(double x, double z, long seed) {
        return new WispPath(x, z, seed);
    }

    private WispPath(double x, double z, long seed) {
        var random = Randomizer.create(seed);
        this.lifetime = MIN_LIFETIME + random.nextInt(MAX_LIFETIME - MIN_LIFETIME + 1);
        this.hauntTicks = MIN_HAUNT_TICKS + random.nextInt(MAX_HAUNT_TICKS - MIN_HAUNT_TICKS + 1);
        this.hauntX[0] = x;
        this.hauntZ[0] = z;
        // Spread around where it started, a quarter turn or so apart, so the next is never next to the last
        var turn = random.nextFloat() * Mth.TWO_PI;
        for (int i = 1; i < HAUNTS; i++) {
            turn += Mth.HALF_PI + (random.nextFloat() - 0.5F);
            var distance = Mth.lerp(random.nextFloat(), MIN_HAUNT_DISTANCE, MAX_HAUNT_DISTANCE);
            this.hauntX[i] = x + Mth.cos(turn) * distance;
            this.hauntZ[i] = z + Mth.sin(turn) * distance;
        }
        this.groupRate = Mth.TWO_PI / Mth.lerp(random.nextFloat(), MIN_GROUP_TICKS, MAX_GROUP_TICKS) * (random.nextBoolean() ? 1F : -1F);
        this.groupPhase = random.nextFloat() * Mth.TWO_PI;
        this.lights = new Light[MIN_LIGHTS + random.nextInt(MAX_LIGHTS - MIN_LIGHTS + 1)];
        for (int i = 0; i < this.lights.length; i++) {
            var blinkTicks = MIN_BLINK_TICKS + random.nextInt(MAX_BLINK_TICKS - MIN_BLINK_TICKS + 1);
            this.lights[i] = new Light(
                    Mth.lerp(random.nextFloat(), MIN_LOOP, MAX_LOOP),
                    Mth.TWO_PI / Mth.lerp(random.nextFloat(), MIN_LOOP_TICKS, MAX_LOOP_TICKS) * (random.nextBoolean() ? 1F : -1F),
                    random.nextFloat() * Mth.TWO_PI,
                    Mth.lerp(random.nextFloat(), MIN_LIFT, MAX_LIFT),
                    Mth.TWO_PI / Mth.lerp(random.nextFloat(), MIN_BOB_TICKS, MAX_BOB_TICKS),
                    random.nextFloat() * Mth.TWO_PI,
                    blinkTicks, random.nextInt(blinkTicks), random.nextLong());
        }
    }

    @Override
    public int lifetime() {
        return this.lifetime;
    }

    public int lights() {
        return this.lights.length;
    }

    /**
     * Where its group is at an age, at the haunt it is at then
     */
    @Override
    public double x(float age) {
        return this.groupX(this.hauntAt(age), age);
    }

    @Override
    public double z(float age) {
        return this.groupZ(this.hauntAt(age), age);
    }

    /**
     * The haunt it is at, at an age
     */
    public int hauntAt(float age) {
        return Math.floorMod((int) (age / this.hauntTicks), HAUNTS);
    }

    /**
     * How much it shows as it goes from one haunt to the next, 0 to 1: out at the change, and showing again after
     */
    public float hauntFade(float age) {
        var t = age % this.hauntTicks;
        // Not at the very start: it is already fading in with its life
        if (age < this.hauntTicks)
            return Mth.clamp((this.hauntTicks - t) / HAUNT_FADE, 0F, 1F);
        return Mth.clamp(Math.min(t, this.hauntTicks - t) / HAUNT_FADE, 0F, 1F);
    }

    /**
     * Where a haunt is
     */
    public double hauntX(int haunt) {
        return this.hauntX[haunt];
    }

    public double hauntZ(int haunt) {
        return this.hauntZ[haunt];
    }

    /**
     * Where its group is at a haunt, at an age: looping slowly around it
     */
    public double groupX(int haunt, float age) {
        return this.hauntX[haunt] + Mth.cos(age * this.groupRate + this.groupPhase) * GROUP_LOOP;
    }

    public double groupZ(int haunt, float age) {
        return this.hauntZ[haunt] + Mth.sin(age * this.groupRate + this.groupPhase) * GROUP_LOOP;
    }

    /**
     * Where a light is at a haunt, at an age: in its own loop around the group, from somewhere new each time it shows
     * again after going out
     */
    public double lightX(int light, int haunt, float age) {
        var l = this.lights[light];
        return this.groupX(haunt, age) + Mth.cos(this.loopAngle(l, age)) * l.loop();
    }

    public double lightZ(int light, int haunt, float age) {
        var l = this.lights[light];
        return this.groupZ(haunt, age) + Mth.sin(this.loopAngle(l, age)) * l.loop();
    }

    /**
     * How high a light floats over what is under it, at an age, in blocks
     */
    public float lift(int light, float age) {
        var l = this.lights[light];
        return l.lift() + BOB * Mth.sin(age * l.bobRate() + l.bobPhase());
    }

    /**
     * How bright a light is at an age, 0 to 1: showing and going out with its life and its haunts, going out now and
     * then on its own, and wavering
     */
    public float brightness(int light, float age) {
        if (age <= 0F || age >= this.lifetime)
            return 0F;
        var l = this.lights[light];
        var life = Math.min(1F, Math.min(age, this.lifetime - age) / FADE_TICKS);
        var waver = 1F - WAVER + WAVER * Mth.sin(age * WAVER_RATE + l.loopPhase());
        return life * this.hauntFade(age) * this.blink(l, age) * waver;
    }

    // Out for a moment at the end of each of its blink spans
    private float blink(Light l, float age) {
        var t = (age + l.blinkPhase()) % l.blinkTicks();
        var dark = l.blinkTicks() - DARK_TICKS;
        if (t < dark - BLINK_FADE)
            return t < BLINK_FADE ? t / BLINK_FADE : 1F;
        return Mth.clamp((dark - t) / BLINK_FADE, 0F, 1F);
    }

    // Each blink span starts the light somewhere new in its loop, a jump made while it is out
    private float loopAngle(Light l, float age) {
        long span = (long) Math.floor((age + l.blinkPhase()) / l.blinkTicks());
        var jump = (Mixers.splitMix64(l.blinkSeed() ^ span) >>> 40) / (float) (1L << 24) * Mth.TWO_PI;
        return age * l.loopRate() + l.loopPhase() + jump;
    }
}

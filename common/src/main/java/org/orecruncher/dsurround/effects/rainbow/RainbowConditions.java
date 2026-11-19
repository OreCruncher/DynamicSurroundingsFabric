// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.rainbow;

import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.runtime.weather.StormIntensity;

/**
 * Whether there is a rainbow, and how strong: a rainbow needs the sun low behind the viewer, shining, and rain in front
 * of them. In the game, that is the rain thinning out: as a storm eases to a drizzle (the rain level falling below
 * {@link #FADING}), the sun breaks through and the rain still falling, now passing on, makes a bow. It lasts while the
 * rain stays light, and fades over {@link #CLEARING_TICKS} once the rain has stopped, as the shower moves away. Rain
 * picking up again ends it.
 * <p>
 * Not every clearing has one: each has {@code chance} of one, picked from the game time it began at, so every player
 * in a world sees the same. The secondary bow shows only after a strong storm (one that reached past
 * {@link #STRONG_STORM}), whose large drops make it bright enough to see.
 * <p>
 * The sun's part: it must be up, and lower than the bow's 42 degrees, or the whole bow is below the horizon; with a
 * secondary bow, lower than its 51 degrees, as its top still shows a little above the horizon after the primary has
 * sunk below it. Ticked on
 * the client thread with the storm's strength (the simulated rain level).
 */
public final class RainbowConditions {

    /**
     * The rain level below which the sun breaks through: a drizzle
     */
    static final float FADING = 0.3F;
    /**
     * How strong the rain must have been to leave a rainbow behind as it clears
     */
    static final float MIN_STORM = 0.1F;
    /**
     * A storm stronger than this leaves a secondary bow too
     */
    static final float STRONG_STORM = StormIntensity.HEAVY.level();
    /**
     * Ticks a rainbow lasts once the rain has stopped: three minutes
     */
    static final int CLEARING_TICKS = 3600;
    /**
     * Ticks over which the rain level is compared to tell whether it is falling
     */
    static final int TREND_TICKS = 40;
    /**
     * Game ticks to a slot: clearings beginning in the same slot share their chance of a rainbow
     */
    static final long SLOT_TICKS = 1200L;
    /**
     * The highest the sun can be for any of the bow to show, in degrees, with a little to spare
     */
    static final float HIGHEST_SUN = 43F;
    /**
     * The highest the sun can be with a secondary bow, in degrees: its top, at 51 degrees from the point opposite the
     * sun, is still above the horizon below that
     */
    static final float HIGHEST_SUN_SECONDARY = 51F;
    /**
     * Degrees over which the bow fades in as the sun rises from the horizon, and out as it sets
     */
    static final float SUN_FADE = 3F;

    // The most the rain has reached since it began
    private float stormPeak;
    // The game time the rain last fell at, and the rain level TREND_TICKS ago
    private long lastRain = Long.MIN_VALUE;
    private final float[] history = new float[TREND_TICKS];
    private int historyAt;
    private int historyCount;
    // Whether the rain is clearing, since when, and whether this clearing has a rainbow
    private boolean clearing;
    private boolean hasRainbow;
    private boolean secondary;
    private float rainLevel;
    // Where the bow's patches are, from the slot the clearing began in
    private float seed;

    /**
     * Takes the tick's rain level, the storm's strength (0 when it isn't raining)
     *
     * @param gameTime  the world's game time, the same for every player
     * @param rainLevel how hard it is raining, 0 to 1
     * @param chance    how likely a clearing is to have a rainbow, 0 to 1
     */
    public void update(long gameTime, float rainLevel, float chance) {
        float before = this.historyCount == TREND_TICKS ? this.history[this.historyAt] : Float.NaN;
        this.history[this.historyAt] = rainLevel;
        this.historyAt = (this.historyAt + 1) % TREND_TICKS;
        this.historyCount = Math.min(this.historyCount + 1, TREND_TICKS);
        this.rainLevel = rainLevel;

        if (rainLevel > 0F) {
            this.lastRain = gameTime;
            this.stormPeak = Math.max(this.stormPeak, rainLevel);
        }

        if (this.clearing) {
            // Rain picking up again, or the shower long gone, ends it
            if (rainLevel >= FADING || rainLevel <= 0F && gameTime - this.lastRain > CLEARING_TICKS) {
                this.clearing = false;
                this.hasRainbow = false;
                this.secondary = false;
                if (rainLevel <= 0F)
                    this.stormPeak = 0F;
            }
        } else if (this.stormPeak >= MIN_STORM && rainLevel < FADING && (rainLevel <= 0F || rainLevel < before)) {
            // The rain has eased to a drizzle, or stopped
            this.clearing = true;
            long slot = gameTime / SLOT_TICKS;
            this.hasRainbow = Randomizer.create(slot * 0x9E3779B97F4A7C15L + 0x7261696EL).nextFloat() < chance;
            this.seed = Math.floorMod(slot, 1000L);
            this.secondary = this.stormPeak > STRONG_STORM;
        } else if (rainLevel <= 0F && gameTime - this.lastRain > CLEARING_TICKS) {
            // A storm long over that never cleared (a rain level that dropped straight from strong to nothing is a
            // clearing, so this is only one too weak to count)
            this.stormPeak = 0F;
        }
    }

    /**
     * How strong a rainbow there is from the weather, 0 to 1: as the rain thins out the sun shines more fully, and once
     * it has stopped the shower moves away, the bow fading with it
     *
     * @param gameTime the world's game time
     */
    public float strength(long gameTime) {
        if (!this.clearing || !this.hasRainbow)
            return 0F;
        float sunshine = smoothstep(FADING, FADING * 0.4F, this.rainLevel);
        if (this.rainLevel > 0F)
            return sunshine;
        float left = 1F - (gameTime - this.lastRain) / (float) CLEARING_TICKS;
        return Math.clamp(left * 3F, 0F, 1F);
    }

    /**
     * Whether the secondary bow shows: only after a strong storm
     */
    public boolean secondary() {
        return this.clearing && this.hasRainbow && this.secondary;
    }

    /**
     * Whether the rain is clearing, rainbow or not
     */
    public boolean isClearing() {
        return this.clearing;
    }

    /**
     * Whether this clearing was picked to have a rainbow
     */
    public boolean hasRainbow() {
        return this.clearing && this.hasRainbow;
    }

    /**
     * Where this clearing's bow has its patches, the same for every player: 0 to 999
     */
    public float seed() {
        return this.seed;
    }

    /**
     * The most the rain reached since it began
     */
    public float stormPeak() {
        return this.stormPeak;
    }

    public void reset() {
        this.stormPeak = 0F;
        this.lastRain = Long.MIN_VALUE;
        this.historyAt = 0;
        this.historyCount = 0;
        this.clearing = false;
        this.hasRainbow = false;
        this.secondary = false;
        this.rainLevel = 0F;
        this.seed = 0F;
    }

    /**
     * How much of a rainbow the sun allows, 0 to 1: none while it is down or higher than the bow (which is then all
     * below the horizon), fading in over its first {@link #SUN_FADE} degrees above the horizon
     *
     * @param elevation how high the sun is, in degrees
     * @param secondary whether there is a secondary bow, which shows with the sun higher than the primary does
     */
    public static float sunFactor(float elevation, boolean secondary) {
        if (elevation <= 0F || elevation >= (secondary ? HIGHEST_SUN_SECONDARY : HIGHEST_SUN))
            return 0F;
        return Math.min(elevation / SUN_FADE, 1F);
    }

    /**
     * How high the sun is, in degrees, from the sun's celestial angle in degrees (noon 0, midnight 180)
     */
    public static float sunElevation(float celestialAngle) {
        return (float) Math.toDegrees(Math.asin(Math.cos(Math.toRadians(celestialAngle))));
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0F, 1F);
        return t * t * (3F - 2F * t);
    }
}

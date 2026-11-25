// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.util.Mth;

/**
 * What the sky's clouds are like: how much of it they cover, how low their base is, how thick they are and how high
 * they tower, and the thin streaks high above them. Fair weather has scattered puffs that build up through the
 * afternoon and flatten out at night, more of them on some days than others and in wet biomes than dry ones. A storm
 * closes them into a deck that lowers and thickens with its strength; a thunderstorm's towers far above the rest.
 * Nothing here draws (see {@code effects.clouds}).
 *
 * @param coverage     the share of the sky the low clouds cover, 0 to 1
 * @param baseDrop     how far below the dimension's cloud height their base is, in blocks
 * @param minThickness how thick the thinnest of them are, in blocks
 * @param maxThickness how thick the thickest of them are, in blocks
 * @param cirrus       the share of the sky the high streaks cover, 0 to 1
 * @param overcast     how gray and dim the light on them is, 0 for a clear day to 1 for a thunderstorm
 */
public record CloudCover(float coverage, float baseDrop, float minThickness, float maxThickness, float cirrus,
                         float overcast) {

    /**
     * The thickness of a fair weather cloud that hasn't built up at all: vanilla's
     */
    public static final float FLAT = 4F;
    /**
     * How thick the tallest fair weather clouds are, at the height of a wet afternoon, in blocks
     */
    static final float FAIR_TOWER = 28F;
    /**
     * How thick a full storm's deck is at least, and at most where it is thickest, in blocks
     */
    static final float STORM_MIN = 16F;
    static final float STORM_MAX = 40F;
    /**
     * How much higher a thunderstorm's towers reach, at full thunder, in blocks
     */
    static final float THUNDER_TOWER = 56F;
    /**
     * How far a full storm's base lowers, and a thunderstorm's further, in blocks
     */
    static final float STORM_DROP = 24F;
    static final float THUNDER_DROP = 8F;
    /**
     * The storm strength by which the sky has closed over: even a drizzle comes from an overcast sky
     */
    static final float CLOSED_BY = 0.5F;
    /**
     * The share of the sky a storm's deck covers: a few gaps are left
     */
    static final float STORM_COVERAGE = 0.97F;
    // The fewest and the most fair weather clouds, on the clearest and the cloudiest days
    static final float FAIR_LEAST = 0.15F;
    static final float FAIR_MOST = 0.6F;

    /**
     * Times of day for fair weather clouds, as the sun's angle in degrees (0 at noon, 180 at midnight): building up
     * from {@link #BUILDS_FROM}, highest at {@link #BUILT_AT}, settled by {@link #SETTLED_AT}. By the sun, not the
     * clock, so they keep to the afternoon where a mod changes how long the day is (Ecliptic Seasons' short winter days
     * peaked near sunset by the clock). With vanilla's day these are where its sun is at 1000, 8000 and 14000 ticks
     * (7 am, 2 pm and 8 pm), when they always built and settled.
     */
    static final float BUILDS_FROM = 297.55F;
    static final float BUILT_AT = 22.06F;
    static final float SETTLED_AT = 110.04F;
    // How built up they are through the night
    static final float NIGHT_CONVECTION = 0.1F;

    public static final CloudCover CLEAR = new CloudCover(0F, 0F, FLAT, FLAT, 0F, 0F);

    /**
     * The clouds for the weather
     *
     * @param rainLevel the storm's strength, 0 to 1 (see {@link IWeatherSimulation#rainLevel})
     * @param thunder   the thunder level, 0 to 1
     * @param sunAngle  the sun's angle, in degrees: 0 at noon, 180 at midnight (the environment attribute's)
     * @param downfall  the biome's downfall, 0 (a desert) to 1 (a jungle)
     * @param dayCover  how cloudy the day is, 0 to 1 (see {@link #dayCover})
     */
    public static CloudCover of(float rainLevel, float thunder, float sunAngle, float downfall, float dayCover) {
        rainLevel = Mth.clamp(rainLevel, 0F, 1F);
        thunder = Mth.clamp(thunder, 0F, 1F);
        float wet = Mth.clamp(downfall, 0F, 1F);

        // Fair weather: more clouds on cloudy days and in wet places, building up through the afternoon
        float fairCoverage = Mth.clamp(Mth.lerp(dayCover, FAIR_LEAST, FAIR_MOST) * Mth.lerp(wet, 0.6F, 1.2F), 0F, 1F);
        float building = convection(sunAngle) * Mth.lerp(wet, 0.4F, 1F);
        float fairMax = FLAT + (FAIR_TOWER - FLAT) * building;

        // A storm closes them over, lowers their base and thickens them; thunder raises towers above the deck
        float closed = smoothstep(rainLevel / CLOSED_BY);
        float coverage = Mth.lerp(closed, fairCoverage, STORM_COVERAGE);
        float baseDrop = STORM_DROP * rainLevel + THUNDER_DROP * thunder;
        float minThickness = Mth.lerp(rainLevel, FLAT, STORM_MIN);
        float maxThickness = Math.max(minThickness, Mth.lerp(rainLevel, fairMax, Math.max(fairMax, STORM_MAX)))
                + THUNDER_TOWER * thunder;
        // The high streaks: more of them on cloudy days, hidden by a deck
        float cirrus = Mth.lerp(dayCover, 0.1F, 0.5F) * (1F - closed);
        float overcast = Mth.clamp(closed * 0.6F + thunder * 0.4F, 0F, 1F);
        return new CloudCover(coverage, baseDrop, minThickness, maxThickness, cirrus, overcast);
    }

    /**
     * How far fair weather clouds have built up for the time of day, 0 to 1: little at night, rising through the
     * morning to the most in the afternoon, and settling through the evening
     *
     * @param sunAngle the sun's angle, in degrees, as {@link #of} takes it
     */
    static float convection(float sunAngle) {
        // How far the sun has come since they began to build, through noon
        float since = sunAngle - BUILDS_FROM;
        since -= 360F * Mth.floor(since / 360F);
        float rising = BUILT_AT + 360F - BUILDS_FROM;
        float settling = SETTLED_AT - BUILT_AT;
        if (since < rising)
            return Mth.lerp(smoothstep(since / rising), NIGHT_CONVECTION, 1F);
        if (since < rising + settling)
            return Mth.lerp(smoothstep((since - rising) / settling), 1F, NIGHT_CONVECTION);
        return NIGHT_CONVECTION;
    }

    /**
     * How cloudy a day is, 0 to 1, the same for every player in a world: each day has its own, and the sky eases from
     * one day's to the next through the night
     *
     * @param dayTime the level's time of day, in ticks, counting the days
     */
    public static float dayCover(long dayTime) {
        long day = Math.floorDiv(dayTime, 24000L);
        float hours = Math.floorMod(dayTime, 24000L) / 1000F;
        // From 6 pm (12000) to 6 am the next day, toward the next day's
        float toNext = hours < 12F ? 0F : smoothstep((hours - 12F) / 12F);
        return Mth.lerp(toNext, cloudinessOf(day), cloudinessOf(day + 1));
    }

    /**
     * A day's own cloudiness, 0 to 1, from its number
     */
    static float cloudinessOf(long day) {
        long h = (day + 0x632BE59BD9B4E019L) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    private static float smoothstep(float t) {
        t = Mth.clamp(t, 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    /**
     * These clouds with a coverage and the thickness of the thickest set, for seeing how they look (/dsweather clouds)
     *
     * @param coverage  the share of the sky covered, 0 to 1, or NaN to leave it
     * @param thickness how thick the thickest are, in blocks, or NaN to leave it
     */
    public CloudCover held(float coverage, float thickness) {
        float cover = Float.isNaN(coverage) ? this.coverage : Mth.clamp(coverage, 0F, 1F);
        float max = Float.isNaN(thickness) ? this.maxThickness : Math.max(thickness, 1F);
        float min = Math.min(this.minThickness, max);
        return new CloudCover(cover, this.baseDrop, min, max, this.cirrus, this.overcast);
    }
}

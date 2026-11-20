// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.halo;

import org.orecruncher.dsurround.lib.random.Randomizer;

/**
 * When the sun has a halo and sun dogs. They come from ice crystals in the air: tumbling ones bend the light into a ring
 * 22 degrees around the sun, and flat ones drifting level, like falling leaves, into a bright spot either side of it.
 * The crystals are in thin high cloud, or, in bitter cold, in the clear air itself (diamond dust), so in the game they
 * show in cold biomes (where it would snow) while it isn't snowing, under a clear sky: with the mod's clouds drawn, they
 * fade out as the clouds cover more of it ({@link #clearSky}), as thicker cloud would hide them.
 * <p>
 * Not every stretch of cold fair weather has them: each {@link #SLOT_TICKS} of the game's time has {@code chance} of
 * them, picked from the slot, so every player in a world sees the same.
 * <p>
 * The sun's part: up, fading in over its first degrees above the horizon. The halo shows with the sun at any height
 * the game's sun reaches here; the sun dogs move further out as the sun climbs, and fade, gone above
 * {@link #HIGHEST_SUN_DOGS} (the flat crystals bend no light into them past about 61 degrees).
 */
public final class HaloConditions {

    /**
     * Game ticks to a slot: a quarter of a day, so a morning and an afternoon each have their own chance
     */
    static final long SLOT_TICKS = 6000L;
    /**
     * Degrees over which they fade in as the sun rises from the horizon, and out as it sets
     */
    static final float SUN_FADE = 3F;
    /**
     * The highest the sun can be for any of a sun dog to show, in degrees, and where they start to fade
     */
    static final float HIGHEST_SUN_DOGS = 55F;
    static final float SUN_DOGS_FADE_FROM = 35F;
    /**
     * The ice's refractive index for red, green and blue light
     */
    static final float[] ICE = {1.3065F, 1.3105F, 1.3170F};
    /**
     * The share of the sky the low clouds cover from which halos fade, and by which they are gone: a fair day's
     * clouds cover from 0.15 to 0.6 of it ({@code CloudCover})
     */
    static final float CLEAR_UNTIL = 0.25F;
    static final float CLOUDED_BY = 0.5F;

    private HaloConditions() {
    }

    /**
     * Whether the slot the game time is in has a halo, the same for every player
     *
     * @param gameTime the world's game time
     * @param chance   how likely a slot is to have one, 0 to 1
     */
    public static boolean slotHasHalo(long gameTime, float chance) {
        long slot = Math.floorDiv(gameTime, SLOT_TICKS);
        return Randomizer.create(slot * 0x9E3779B97F4A7C15L + 0x68616C6FL).nextFloat() < chance;
    }

    /**
     * How much of a halo the clouds allow, 0 to 1: whole under a clear sky, fading as the low clouds spread, none once
     * they cover {@link #CLOUDED_BY} of it
     *
     * @param coverage the share of the sky the low clouds cover, 0 to 1
     */
    public static float clearSky(float coverage) {
        float t = Math.clamp((CLOUDED_BY - coverage) / (CLOUDED_BY - CLEAR_UNTIL), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    /**
     * How much of a halo the sun allows, 0 to 1: none while it is down, fading in over its first {@link #SUN_FADE}
     * degrees
     *
     * @param elevation how high the sun is, in degrees
     */
    public static float sunFactor(float elevation) {
        if (elevation <= 0F)
            return 0F;
        return Math.min(elevation / SUN_FADE, 1F);
    }

    /**
     * How strongly the sun dogs show for the sun's height, 0 to 1, besides {@link #sunFactor}: whole with the sun low,
     * fading as it climbs, none above {@link #HIGHEST_SUN_DOGS}
     *
     * @param elevation how high the sun is, in degrees
     */
    public static float sunDogFactor(float elevation) {
        float t = Math.clamp((HIGHEST_SUN_DOGS - elevation) / (HIGHEST_SUN_DOGS - SUN_DOGS_FADE_FROM), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    /**
     * How far round from the sun a sun dog is, in degrees, level with it, for light of this refractive index: 22
     * degrees with the sun on the horizon, further as it climbs. Light passes through a flat crystal's side faces, a
     * 60 degree prism, at a slant as the sun rises, which bends it as a prism of a higher index would (Bravais' index)
     *
     * @param elevation how high the sun is, in degrees
     * @param index     the ice's refractive index for the light
     * @return the angle, or NaN where no light reaches one
     */
    public static float sunDogAzimuth(float elevation, float index) {
        double e = Math.toRadians(elevation);
        double sin = Math.sin(e);
        double effective = Math.sqrt(index * index - sin * sin) / Math.cos(e);
        double half = effective * 0.5D;
        if (half >= 1D)
            return Float.NaN;
        return (float) (2D * Math.toDegrees(Math.asin(half)) - 60D);
    }
}

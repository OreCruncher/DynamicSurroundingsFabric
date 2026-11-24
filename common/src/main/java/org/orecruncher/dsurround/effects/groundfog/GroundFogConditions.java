// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.groundfog;

import net.minecraft.util.Mth;

/**
 * How strong the ground fog is for the conditions, 0 to 1, as a share of what the biomes give it: thickest at night and
 * around dawn, burning off by mid-morning to a thin persistent haze; thinned by a strong wind and broken up by rain;
 * and gone under cover, where the player is out of it.
 */
public final class GroundFogConditions {

    /**
     * Times of day, as the sun's angle in degrees (0 at noon, 180 at midnight): thin through the day, thickening from
     * {@link #GATHERS_FROM} to full at {@link #GATHERED_AT} and through the night, full until {@link #BURNS_OFF_FROM},
     * thinning until {@link #BURNED_OFF_AT}. By the sun, not the clock, so the mist keeps to sunrise and sunset where a
     * mod changes how long the day is (Ecliptic Seasons, by the time of year). With vanilla's day these are where its
     * sun is at 11000, 13000, 1000 and 3000 ticks (its timeline's eased sun angle), so it gathers and burns off when it
     * always did.
     */
    static final float GATHERS_FROM = 62.45F;
    static final float GATHERED_AT = 93.52F;
    static final float BURNS_OFF_FROM = 297.55F;
    static final float BURNED_OFF_AT = 325.4F;
    /**
     * How much of the mist is left through the day: it lingers, thin, where it gathers
     */
    static final float DAYTIME = 0.35F;
    /**
     * The wind's speed, as a percent of a full storm's, from which it thins the mist, and at which it has thinned it
     * the most, to {@link #WINDIEST}
     */
    static final float WIND_THINS_FROM = 60F;
    static final float WIND_THINS_TO = 200F;
    static final float WINDIEST = 0.3F;
    /**
     * How much of the mist a storm at full strength breaks up
     */
    static final float RAIN_BREAKS_UP = 0.6F;

    private GroundFogConditions() {
    }

    /**
     * How much of the mist there is at a time of day
     *
     * @param sunAngle the sun's angle, in degrees: 0 at noon, 180 at midnight (the environment attribute's)
     */
    public static float timeOfDay(float sunAngle) {
        float angle = sunAngle - 360F * Mth.floor(sunAngle / 360F);
        if (angle >= GATHERED_AT && angle < BURNS_OFF_FROM)
            return 1F;
        if (angle >= BURNS_OFF_FROM && angle < BURNED_OFF_AT)
            return Mth.lerp(ease((angle - BURNS_OFF_FROM) / (BURNED_OFF_AT - BURNS_OFF_FROM)), 1F, DAYTIME);
        if (angle >= GATHERS_FROM && angle < GATHERED_AT)
            return Mth.lerp(ease((angle - GATHERS_FROM) / (GATHERED_AT - GATHERS_FROM)), DAYTIME, 1F);
        return DAYTIME;
    }

    /**
     * How much of the mist is left in a wind
     *
     * @param windPercent the steady wind's speed, as a percent of a full storm's
     */
    public static float wind(float windPercent) {
        float t = Mth.clamp((windPercent - WIND_THINS_FROM) / (WIND_THINS_TO - WIND_THINS_FROM), 0F, 1F);
        return Mth.lerp(ease(t), 1F, WINDIEST);
    }

    /**
     * How much of the mist is left in rain
     *
     * @param rainLevel the storm's strength, 0 to 1
     */
    public static float rain(float rainLevel) {
        return 1F - RAIN_BREAKS_UP * Mth.clamp(rainLevel, 0F, 1F);
    }

    /**
     * How strong the mist is, all told
     *
     * @param sunAngle the sun's angle, in degrees, as {@link #timeOfDay} takes it
     * @param exposure how open to the sky the player is, 0 to 1: under cover, out of the mist
     */
    public static float strength(float sunAngle, float windPercent, float rainLevel, float exposure) {
        return timeOfDay(sunAngle) * wind(windPercent) * rain(rainLevel) * Mth.clamp(exposure, 0F, 1F);
    }

    private static float ease(float t) {
        return t * t * (3F - 2F * t);
    }
}

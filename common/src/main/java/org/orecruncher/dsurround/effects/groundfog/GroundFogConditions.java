package org.orecruncher.dsurround.effects.groundfog;

import net.minecraft.util.Mth;

/**
 * How strong the ground fog is for the conditions, 0 to 1, as a share of what the biomes give it: thickest at night and
 * around dawn, burning off by mid-morning to a thin persistent haze; thinned by a strong wind and broken up by rain;
 * and gone under cover, where the player is out of it.
 */
public final class GroundFogConditions {

    /**
     * Times of day, in ticks from sunrise: full until {@link #BURNS_OFF_FROM}, thinning until {@link #BURNED_OFF_AT},
     * thin through the day, thickening again from {@link #GATHERS_FROM} to full at {@link #GATHERED_AT} and through the
     * night
     */
    static final int BURNS_OFF_FROM = 1000;
    static final int BURNED_OFF_AT = 3000;
    static final int GATHERS_FROM = 11000;
    static final int GATHERED_AT = 13000;
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
     * @param dayTime the level's time of day, in ticks; only the time within the day counts
     */
    public static float timeOfDay(long dayTime) {
        int time = (int) Math.floorMod(dayTime, 24000L);
        if (time < BURNS_OFF_FROM || time >= GATHERED_AT)
            return 1F;
        if (time < BURNED_OFF_AT)
            return Mth.lerp(ease((time - BURNS_OFF_FROM) / (float) (BURNED_OFF_AT - BURNS_OFF_FROM)), 1F, DAYTIME);
        if (time < GATHERS_FROM)
            return DAYTIME;
        return Mth.lerp(ease((time - GATHERS_FROM) / (float) (GATHERED_AT - GATHERS_FROM)), DAYTIME, 1F);
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
     * @param exposure how open to the sky the player is, 0 to 1: under cover, out of the mist
     */
    public static float strength(long dayTime, float windPercent, float rainLevel, float exposure) {
        return timeOfDay(dayTime) * wind(windPercent) * rain(rainLevel) * Mth.clamp(exposure, 0F, 1F);
    }

    private static float ease(float t) {
        return t * t * (3F - 2F * t);
    }
}

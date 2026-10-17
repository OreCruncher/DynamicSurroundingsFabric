package org.orecruncher.dsurround.runtime.weather;

/**
 * Lets tests outside this package set a wind
 */
public final class WindAccess {

    private WindAccess() {
    }

    /**
     * A steady wind blowing toward {@code angle}, in radians (0 is east, toward positive x), at {@code speed}, with no
     * gust
     */
    public static void set(Wind wind, float angle, float speed) {
        wind.set(angle, speed, 1);
        wind.gust(0, 0F);
    }

    /**
     * A steady wind as {@link #set}, keeping any gust under way
     */
    public static void setSteady(Wind wind, float angle, float speed) {
        wind.set(angle, speed, 1);
    }

    /**
     * Starts a gust lasting {@code ticks}, {@code power} stronger than the steady wind at its height; 0 ticks ends any
     */
    public static void gust(Wind wind, int ticks, float power) {
        wind.gust(ticks, power);
    }

    /**
     * The strongest steady wind there is: a thunderstorm's, at full strength, raising dust
     */
    public static float strongest() {
        return Wind.MAX_SPEED * Wind.DUST_FACTOR * Wind.THUNDER_FACTOR;
    }
}

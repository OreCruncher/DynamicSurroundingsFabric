package org.orecruncher.dsurround.effects.precipitation;

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
}

package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.util.Mth;

/**
 * How loud the wind's ambient loops play for the wind there is: the sounds whose factories follow the wind (see
 * {@code followsWind} in the sound factories) are scaled by this, on top of how much of the area their biome covers.
 */
public final class WindSounds {

    /**
     * In a calm, a share of the loop's volume: a windy biome still sounds windy between storms
     */
    static final float CALM_FLOOR = 0.4F;
    /**
     * Beyond a full storm's wind (thunderstorms, dust storms), how much louder it gets for each further full storm's
     * worth, and the most it gets
     */
    static final float BEYOND_RATE = 0.3F;
    static final float LOUDEST = 1.3F;
    /**
     * Under cover, out of the wind, a share of the volume: still heard through the walls in a gale
     */
    static final float SHELTERED = 0.5F;

    private WindSounds() {
    }

    /**
     * The volume scale for wind loops
     *
     * @param speed    the wind's speed where the player is, gusts included, in blocks a tick
     * @param exposure how open to the sky the player is, 0 to 1, as {@link Shelter#exposure} gives it
     */
    public static float loopScale(float speed, float exposure) {
        var share = Math.max(speed, 0F) / Wind.speedOfPercent(100);
        var scale = share <= 1F
                ? Mth.lerp(share, CALM_FLOOR, 1F)
                : Math.min(1F + (share - 1F) * BEYOND_RATE, LOUDEST);
        return scale * Mth.lerp(Mth.clamp(exposure, 0F, 1F), SHELTERED, 1F);
    }
}

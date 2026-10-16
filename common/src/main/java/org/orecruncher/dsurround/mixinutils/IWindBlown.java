package org.orecruncher.dsurround.mixinutils;

/**
 * A particle of vanilla's or another mod's that drifts with the wind ({@code ParticleTags.WIND_BLOWN}), marked as the
 * particle engine makes it. The mod's own particles take the wind themselves, and are never marked.
 */
public interface IWindBlown {
    /**
     * Starts the particle drifting with the wind, as far as it is out in it
     *
     * @param exposure from {@code Shelter.exposure}, where it was made: above 0
     */
    void dsurround$blowInWind(float exposure);
}

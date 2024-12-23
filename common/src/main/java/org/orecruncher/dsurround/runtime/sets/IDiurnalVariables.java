package org.orecruncher.dsurround.runtime.sets;

@SuppressWarnings("unused")
public interface IDiurnalVariables {

    boolean isDay();

    boolean isNight();

    boolean isSunrise();

    boolean isSunset();

    float getMoonPhaseFactor();

    float getCelestialAngle();
}
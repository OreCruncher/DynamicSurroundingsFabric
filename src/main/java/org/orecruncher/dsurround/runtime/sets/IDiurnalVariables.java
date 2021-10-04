package org.orecruncher.dsurround.runtime.sets;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface IDiurnalVariables {

    boolean isDay();

    boolean isNight();

    boolean isSunrise();

    boolean isSunset();

    float getMoonPhaseFactor();

    float getCelestialAngle();
}
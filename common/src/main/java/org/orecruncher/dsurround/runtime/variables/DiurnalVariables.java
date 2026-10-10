package org.orecruncher.dsurround.runtime.variables;

import org.orecruncher.dsurround.lib.time.DayCycle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;

public final class DiurnalVariables extends VariableSet {

    private final ILevelOracle levelOracle;

    // Numeric values are stored boxed when updated each tick, so that reading them from scripts does not allocate
    private Float moonPhaseFactor = 0F;
    private Float celestialAngle = 0F;
    private boolean isDay;
    private boolean isNight;
    private boolean isSunrise;
    private boolean isSunset;

    public DiurnalVariables(ILevelOracle levelOracle) {
        super("diurnal");
        this.levelOracle = levelOracle;
    }

    @Override
    public void tick() {

        if (GameUtils.isInGame()) {
            DayCycle cycle = this.levelOracle.currentDiurnalState();
            this.isDay = cycle == DayCycle.DAYTIME;
            this.isNight = cycle == DayCycle.NIGHTTIME;
            this.isSunrise = cycle == DayCycle.SUNRISE;
            this.isSunset = cycle == DayCycle.SUNSET;
            this.moonPhaseFactor = this.levelOracle.currentMoonSize();
            // A fraction of the day from noon, 0 to 1, as Level.getTimeOfDay() was; the oracle gives degrees
            this.celestialAngle = this.levelOracle.currentCelestialAngle() / 360F;
        } else {
            this.isDay = false;
            this.isNight = false;
            this.isSunrise = false;
            this.isSunset = false;
            this.moonPhaseFactor = 1F;
            this.celestialAngle = 1F;
        }
    }

    @Override
    public void configure(IConfigureDefinition config) {
        config.property(id("isDay"), () -> this.isDay);
        config.property(id("isNight"), () -> this.isNight);
        config.property(id("isSunrise"), () -> this.isSunrise);
        config.property(id("isSunset"), () -> this.isSunset);
        config.property(id("getMoonPhaseFactor"), () -> this.moonPhaseFactor);
        config.property(id("getCelestialAngle"), () -> this.celestialAngle);
    }
}
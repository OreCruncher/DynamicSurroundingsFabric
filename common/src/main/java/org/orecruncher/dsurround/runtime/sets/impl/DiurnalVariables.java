package org.orecruncher.dsurround.runtime.sets.impl;

import org.orecruncher.dsurround.lib.DayCycle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.IVariableAccess;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.runtime.sets.IDiurnalVariables;

public class DiurnalVariables extends VariableSet<IDiurnalVariables> implements IDiurnalVariables {

    private float moonPhaseFactor;
    private float celestialAngle;
    private boolean isDay;
    private boolean isNight;
    private boolean isSunrise;
    private boolean isSunset;

    public DiurnalVariables() {
        super("diurnal");
    }

    @Override
    public IDiurnalVariables getInterface() {
        return this;
    }

    public void update(IVariableAccess variableAccess) {

        if (GameUtils.isInGame()) {
            var world = GameUtils.getWorld().orElseThrow();
            DayCycle cycle = DayCycle.getCycle(world);
            this.isDay = cycle == DayCycle.DAYTIME;
            this.isNight = cycle == DayCycle.NIGHTTIME;
            this.isSunrise = cycle == DayCycle.SUNRISE;
            this.isSunset = cycle == DayCycle.SUNSET;
            this.moonPhaseFactor = DayCycle.getMoonSize(world);
            this.celestialAngle = world.getTimeOfDay(1F);
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
    public boolean isDay() {
        return this.isDay;
    }

    @Override
    public boolean isNight() {
        return this.isNight;
    }

    @Override
    public boolean isSunrise() {
        return this.isSunrise;
    }

    @Override
    public boolean isSunset() {
        return this.isSunset;
    }

    @Override
    public float getMoonPhaseFactor() {
        return this.moonPhaseFactor;
    }

    @Override
    public float getCelestialAngle() {
        return this.celestialAngle;
    }
}
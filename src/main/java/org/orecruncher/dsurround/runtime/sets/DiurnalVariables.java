package org.orecruncher.dsurround.runtime.sets;

import org.orecruncher.dsurround.lib.DayCycle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Lazy;
import org.orecruncher.dsurround.lib.scripting.IVariableAccess;
import org.orecruncher.dsurround.lib.scripting.VariableSet;

public class DiurnalVariables extends VariableSet<IDiurnalVariables> implements IDiurnalVariables {

    private final Lazy<Float> moonPhaseFactor = new Lazy<>(() -> GameUtils.isInGame() ? DayCycle.getMoonSize(GameUtils.getWorld()) : 0F);
    private final Lazy<Float> celestialAngle = new Lazy<>(() -> GameUtils.isInGame() ? GameUtils.getWorld().getSkyAngle(0F) : 0F);
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
            DayCycle cycle = DayCycle.getCycle(GameUtils.getWorld());
            this.isDay = cycle == DayCycle.DAYTIME;
            this.isNight = cycle == DayCycle.NIGHTTIME;
            this.isSunrise = cycle == DayCycle.SUNRISE;
            this.isSunset = cycle == DayCycle.SUNSET;
        } else {
            this.isDay = false;
            this.isNight = false;
            this.isSunrise = false;
            this.isSunset = false;
        }

        this.moonPhaseFactor.reset();
        this.celestialAngle.reset();
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
        return this.moonPhaseFactor.get();
    }

    @Override
    public float getCelestialAngle() {
        return this.celestialAngle.get();
    }
}
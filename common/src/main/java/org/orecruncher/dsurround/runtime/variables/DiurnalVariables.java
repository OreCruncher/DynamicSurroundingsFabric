package org.orecruncher.dsurround.runtime.variables;

import org.orecruncher.dsurround.lib.DayCycle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;

public class DiurnalVariables extends VariableSet {

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
    public void tick() {

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
    public void configure(IConfigureDefinition config) {
        config.defineFunction(id("isDay"), l -> this.isDay);
        config.defineFunction(id("isNight"), l -> this.isNight);
        config.defineFunction(id("isSunrise"), l -> this.isSunrise);
        config.defineFunction(id("isSunset"), l -> this.isSunset);
        config.defineFunction(id("getMoonPhaseFactor"), l -> this.moonPhaseFactor);
        config.defineFunction(id("getCelestialAngle"), l -> this.celestialAngle);
    }
}
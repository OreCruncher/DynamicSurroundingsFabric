package org.orecruncher.dsurround.runtime.variables;

import org.orecruncher.dsurround.lib.DayCycle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;

public final class DiurnalVariables extends VariableSet {

    // Numeric values are stored boxed when updated each tick, so that reading them from scripts does not allocate
    private Float moonPhaseFactor = 0F;
    private Float celestialAngle = 0F;
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
        config.property(id("isDay"), () -> this.isDay);
        config.property(id("isNight"), () -> this.isNight);
        config.property(id("isSunrise"), () -> this.isSunrise);
        config.property(id("isSunset"), () -> this.isSunset);
        config.property(id("getMoonPhaseFactor"), () -> this.moonPhaseFactor);
        config.property(id("getCelestialAngle"), () -> this.celestialAngle);
    }
}
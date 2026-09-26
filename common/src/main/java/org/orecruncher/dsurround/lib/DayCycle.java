package org.orecruncher.dsurround.lib;

import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Constants;

public enum DayCycle {

    NO_SKY("NoSky"),
    SUNRISE("Sunrise"),
    SUNSET("Sunset"),
    DAYTIME("Daytime"),
    NIGHTTIME("Nighttime");

    // Thresholds are in degrees.  Noon is 0 degrees, and midnight is 180
    private static final float DAYTIME_THRESHOLD = 274;
    private static final float SUNRISE_THRESHOLD = DAYTIME_THRESHOLD - 15F;
    private static final float NIGHTTIME_THRESHOLD = 94F;
    private static final float SUNSET_THRESHOLD = NIGHTTIME_THRESHOLD - 15F;

    private final String localizeString;

    DayCycle(final String localName) {
        this.localizeString = Constants.MOD_ID + ".format." + localName;
    }

    public static DayCycle getCycle(final Level world, Vec3 position) {
        if (world.dimensionType().hasCeiling() || !world.dimensionType().hasSkyLight())
            return DayCycle.NO_SKY;

        final float angleDegrees = getCelestialAngle(world, position);

        if (angleDegrees > DAYTIME_THRESHOLD)
            return DayCycle.DAYTIME;
        if (angleDegrees > SUNRISE_THRESHOLD)
            return DayCycle.SUNRISE;
        if (angleDegrees > NIGHTTIME_THRESHOLD)
            return DayCycle.NIGHTTIME;
        if (angleDegrees > SUNSET_THRESHOLD)
            return DayCycle.SUNSET;
        return DayCycle.DAYTIME;
    }

    public static float getCelestialAngle(final Level world, final Vec3 position) {
        return world.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, position);
    }

    public static float getMoonSize(final Level world, Vec3 position) {
        var phase = world.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, position);
        return switch (phase)
        {
            case MoonPhase.NEW_MOON -> 0F;
            case MoonPhase.WAXING_CRESCENT, MoonPhase.WANING_CRESCENT -> 0.25F;
            case MoonPhase.FIRST_QUARTER, MoonPhase.THIRD_QUARTER -> 0.5F;
            case MoonPhase.WAXING_GIBBOUS, MoonPhase.WANING_GIBBOUS -> 0.75F;
            case MoonPhase.FULL_MOON -> 1F;
        };
    }

    public String getFormattedName() {
        return Localization.load(this.localizeString);
    }

}
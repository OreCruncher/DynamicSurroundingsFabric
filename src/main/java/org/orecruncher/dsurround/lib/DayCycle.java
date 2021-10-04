package org.orecruncher.dsurround.lib;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.world.World;
import org.orecruncher.dsurround.Client;

public enum DayCycle {

    NO_SKY(false, "NoSky"),
    SUNRISE(false, "Sunrise"),
    SUNSET(true, "Sunset"),
    DAYTIME(false, "Daytime"),
    NIGHTTIME(true, "Nighttime");

    private static final float DAYTIME_THRESHOLD = 0.8F;
    private static final float SUNRISE_THRESHOLD = DAYTIME_THRESHOLD - 0.04F;
    private static final float NIGHTTIME_THRESHOLD = 0.26F;
    private static final float SUNSET_THRESHOLD = NIGHTTIME_THRESHOLD - 0.04F;

    private final boolean auroraVisible;
    private final String localizeString;

    DayCycle(final boolean auroraVisible, final String localName) {
        this.auroraVisible = auroraVisible;
        this.localizeString = Client.ModId + ".format." + localName;
    }

    public static boolean isDaytime(final World world) {
        return getCycle(world) == DayCycle.DAYTIME;
    }

    public static boolean isNighttime(final World world) {
        return getCycle(world) == DayCycle.NIGHTTIME;
    }

    public static boolean isSunrise(final World world) {
        return getCycle(world) == DayCycle.SUNRISE;
    }

    public static boolean isSunset(final World world) {
        return getCycle(world) == DayCycle.SUNSET;
    }

    public static DayCycle getCycle(final World world) {
        if (world.getDimension().hasCeiling() || !world.getDimension().hasSkyLight())
            return DayCycle.NO_SKY;

        final float angle = world.getSkyAngle(0);

        if (angle > DAYTIME_THRESHOLD)
            return DayCycle.DAYTIME;
        if (angle > SUNRISE_THRESHOLD)
            return DayCycle.SUNRISE;
        if (angle > NIGHTTIME_THRESHOLD)
            return DayCycle.NIGHTTIME;
        if (angle > SUNSET_THRESHOLD)
            return DayCycle.SUNSET;
        return DayCycle.DAYTIME;
    }

    public static float getMoonSize(final World world) {
        return world.getMoonSize();
    }

    public static boolean isAuroraInvisible(final World world) {
        return !getCycle(world).isAuroraVisible();
    }

    public boolean isAuroraVisible() {
        return this.auroraVisible;
    }

    public String getFormattedName() {
        return Localization.load(this.localizeString);
    }

}
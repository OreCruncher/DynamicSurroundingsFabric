package org.orecruncher.dsurround.config.libraries;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;

public interface IDimensionInformation {
    /**
     * The resource location ID of the dimension
     */
    ResourceLocation name();
    /**
     * The client level for the dimension
     */
    ClientLevel level();
    /**
     * The sea level configured for the dimension
     */
    int seaLevel();
    /**
     * Whether the dimension is considered always outside, like Nether.
     */
    boolean alwaysOutside();
    /**
     * The vertical Y level which is the threshold of outer space.
     */
    int getSpaceHeight();
    /**
     * The vertical Y level where clouds are expected to be
     */
    int getCloudHeight();

    /**
     * Indicates whether the compass should "wobble" making the bearing unreadable
     */
    boolean getCompassWobble();

    /**
     * Whether the mod's particle rain, snow and dust storms are drawn here, in place of the dimension's own weather
     */
    boolean particleWeather();

    /**
     * Whether the wind blows here
     */
    boolean simulateWind();

    /**
     * Whether mist lies on the ground here, in the biomes given it
     */
    boolean groundFog();

    /**
     * Whether the mod's clouds are drawn here, in place of vanilla's
     */
    boolean dynamicClouds();
}

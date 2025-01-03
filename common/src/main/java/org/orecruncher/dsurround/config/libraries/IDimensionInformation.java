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
     * The veritical Y level where clouds are expected to be
     */
    int getCloudHeight();
}

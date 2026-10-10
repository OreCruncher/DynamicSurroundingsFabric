package org.orecruncher.dsurround.runtime.oracle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;

public interface IDimensionOracle {
    /**
     * The client level for the dimension
     */
    ClientLevel level();
    /**
     * The resource location ID of the dimension
     */
    Identifier name();
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
     * Indicates if the dimension is natural like OVERWORLD
     */
    boolean natural();
    /**
     * Indicates whether the world is super flat
     */
    boolean isSuperFlat();
}

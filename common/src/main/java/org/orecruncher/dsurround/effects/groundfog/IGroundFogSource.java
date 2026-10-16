package org.orecruncher.dsurround.effects.groundfog;

/**
 * What the ground fog needs to know of the level: where the ground is, how foggy its biome is, and how it is lit. In
 * the game, the player's level; in tests, a fake.
 */
public interface IGroundFogSource {

    /**
     * The height of the first air above the ground at a column, under any trees (mist lies under them, not on their
     * leaves); on water, its surface
     */
    int groundHeight(int x, int z);

    /**
     * How foggy the ground is at a position, 0 to 1: its biome's ground fog
     */
    float density(int x, int y, int z);

    /**
     * The packed light at a position, as the lightmap takes it
     */
    int light(int x, int y, int z);
}

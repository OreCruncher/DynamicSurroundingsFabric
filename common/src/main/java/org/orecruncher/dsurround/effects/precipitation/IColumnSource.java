package org.orecruncher.dsurround.effects.precipitation;

/**
 * What precipitation needs to know about a column of the world. Kept apart from the level so the drops can be tested.
 */
public interface IColumnSource {

    /**
     * From {@link #dustColor}: the block isn't one dust is blown from
     */
    int NOT_A_DUST_SOURCE = -1;

    /**
     * The height precipitation stops at: the top of the highest block that blocks motion
     */
    int groundHeight(int x, int z);

    /**
     * What a storm brings to the position: rain, snow, dust where the biome is dry and sandy, or nothing
     */
    PrecipitationKind precipitation(int x, int y, int z);

    /**
     * In a rain column, the share of what falls that is snowflakes, 0 to 1: sleet, near the snow line. Asked right
     * after {@link #precipitation} for the same position, and only where it said rain.
     */
    default float snowShare(int x, int y, int z) {
        return 0F;
    }

    /**
     * Whether a drop can be at the position: nothing solid and no fluid there. Drops carried in under an overhang
     * drift on through open air until they reach something that isn't. By default, open above the column's ground
     * and solid below it, as if every column were solid all the way up.
     */
    default boolean isOpen(int x, int y, int z) {
        return y >= this.groundHeight(x, z);
    }

    /**
     * Packed sky and block light at the position, as vanilla's renderers use it
     */
    int light(int x, int y, int z);

    /**
     * The color of the dust blown up from the block at the position, as 0xRRGGBB, or {@link #NOT_A_DUST_SOURCE} if
     * dust isn't blown from it (grass, water, stone): only loose ground like sand gives the dust its color
     */
    int dustColor(int x, int y, int z);

    /**
     * The color of the dust where none of the ground around gives it one, as 0xRRGGBB: what the biome at the
     * position is known for, such as sand in a desert and red sand in badlands
     */
    int fallbackDustColor(int x, int y, int z);
}

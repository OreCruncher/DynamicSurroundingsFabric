package org.orecruncher.dsurround.config;

/**
 * Which light the light level overlay shows on each block
 */
public enum LightLevelMode {
    /**
     * Light from torches, lava and other blocks: what decides spawning in most dimensions
     */
    BLOCK,
    /**
     * Light from the sky, as it is now (darker at night and in storms)
     */
    SKY,
    /**
     * The brighter of the two: the light a mob sees
     */
    BLOCK_SKY
}

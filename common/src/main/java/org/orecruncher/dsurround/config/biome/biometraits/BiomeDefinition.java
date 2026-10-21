package org.orecruncher.dsurround.config.biome.biometraits;

/**
 * What a biome's definition file says it grows (see {@link BiomeDefinitions}): what the client is never sent, as a
 * server sends a biome's climate and effects but not what generates in it.
 *
 * @param treesPerChunk the trees placed in a chunk, on average; NaN where a tree feature's count couldn't be worked out
 * @param aquatic       whether it is water (oceans, rivers, swamps): it grows seagrass or kelp, or spawns water
 *                      creatures; there few trees doesn't make the vegetation sparse
 */
public record BiomeDefinition(float treesPerChunk, boolean aquatic) {

    public boolean treesKnown() {
        return !Float.isNaN(this.treesPerChunk);
    }
}

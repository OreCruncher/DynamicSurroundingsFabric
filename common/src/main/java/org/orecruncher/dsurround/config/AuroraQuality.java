// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import org.orecruncher.dsurround.lib.config.ITranslatedEnum;

/**
 * How finely the realistic aurora is drawn, named as vanilla's graphics settings are (and shown with their
 * translations). It is drawn at a share of the screen's size, a pixel for each {@code scale} by {@code scale} of the
 * screen's, then laid over the sky; at half, a quarter of the pixels to work out, and its finest rays a little softer.
 */
public enum AuroraQuality implements ITranslatedEnum {
    FAST("options.graphics.fast", 2, 2),
    FANCY("options.graphics.fancy", 1, 1);

    private final String translationKey;
    private final int scale;
    private final int fieldSamples;

    AuroraQuality(String translationKey, int scale, int fieldSamples) {
        this.translationKey = translationKey;
        this.scale = scale;
        this.fieldSamples = fieldSamples;
    }

    @Override
    public String translationKey() {
        return this.translationKey;
    }

    /**
     * The screen's pixels each way for each of the aurora's: 1 at the screen's size, 2 at half
     */
    public int scale() {
        return this.scale;
    }

    /**
     * How many times over to sample the ray field along a line of sight: a sample that misses a ray between its
     * neighbors shows as a speck, and the fewer the pixels, the larger the speck
     */
    public int fieldSamples() {
        return this.fieldSamples;
    }
}

package org.orecruncher.dsurround.config;

import org.orecruncher.dsurround.lib.config.ITranslatedEnum;

/**
 * How finely the realistic aurora is drawn, named as vanilla's graphics settings are (and shown with their
 * translations). It is drawn at a share of the screen's size, a pixel for each {@code scale} by {@code scale} of the
 * screen's, then laid over the sky; at half, a quarter of the pixels to work out, and its finest rays a little softer.
 */
public enum AuroraQuality implements ITranslatedEnum {
    FAST("options.graphics.fast", 2),
    FANCY("options.graphics.fancy", 1);

    private final String translationKey;
    private final int scale;

    AuroraQuality(String translationKey, int scale) {
        this.translationKey = translationKey;
        this.scale = scale;
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
}

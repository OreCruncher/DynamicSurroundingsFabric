package org.orecruncher.dsurround.config;

import org.orecruncher.dsurround.lib.config.ITranslatedEnum;

/**
 * How finely the ground fog is drawn, named as vanilla's graphics settings are (and shown with their translations):
 * how closely its lines of sight are followed (whether it is drawn at all is {@code weatherOptions.enableGroundFog}).
 * The march (see {@link org.orecruncher.dsurround.effects.groundfog.GroundFogVolume}) is the costly part: it is drawn
 * at a share of the screen's size, a pixel for each {@code scale} by {@code scale} of the screen's, taking at most
 * {@code maxSteps} along each line of sight.
 */
public enum GroundFogQuality implements ITranslatedEnum {
    // Fast's 12 steps rather than 16 cost nothing to see (bench, 2026-10-30: the march 17% cheaper)
    FAST("options.graphics.fast", 3, 12),
    FANCY("options.graphics.fancy", 2, 24),
    FABULOUS("options.graphics.fabulous", 1, 24);

    private final String translationKey;
    private final int scale;
    private final int maxSteps;

    GroundFogQuality(String translationKey, int scale, int maxSteps) {
        this.translationKey = translationKey;
        this.scale = scale;
        this.maxSteps = maxSteps;
    }

    @Override
    public String translationKey() {
        return this.translationKey;
    }

    /**
     * The screen's pixels each way for each of the march's: 1 at the screen's size, 2 at half, 3 at a third
     */
    public int scale() {
        return this.scale;
    }

    /**
     * The most steps taken along a line of sight
     */
    public int maxSteps() {
        return this.maxSteps;
    }
}

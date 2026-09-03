package org.orecruncher.dsurround.config;

import org.orecruncher.dsurround.lib.random.Randomizer;

public enum WaterRippleStyle {

    NONE(true, true),
    PIXELATED_CIRCLE(true, true);

    private final boolean doScaling;
    private final boolean doAlpha;

    WaterRippleStyle(boolean doScaling, boolean doAlpha) {
        this.doScaling = doScaling;
        this.doAlpha = doAlpha;
    }

    public boolean doScaling() {
        return this.doScaling;
    }

    public boolean doAlpha() {
        return this.doAlpha;
    }

    public int getMaxAge() {
        return 12 + Randomizer.current().nextInt(8);
    }
}

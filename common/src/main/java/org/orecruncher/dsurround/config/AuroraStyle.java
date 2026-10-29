package org.orecruncher.dsurround.config;

import org.orecruncher.dsurround.lib.config.ITranslatedEnum;

/**
 * How auroras look: the original Dynamic Surroundings curtains, or one modelled on the real thing.
 */
public enum AuroraStyle implements ITranslatedEnum {
    CLASSIC("dsurround.config.auroraStyle.classic"),
    REALISTIC("dsurround.config.auroraStyle.realistic");

    private final String translationKey;

    AuroraStyle(String translationKey) {
        this.translationKey = translationKey;
    }

    @Override
    public String translationKey() {
        return this.translationKey;
    }
}

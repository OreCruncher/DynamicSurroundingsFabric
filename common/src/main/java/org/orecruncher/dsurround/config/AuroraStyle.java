// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import org.orecruncher.dsurround.lib.config.ITranslatedEnum;

/**
 * How auroras look: the original Dynamic Surroundings curtains, one modeled on the real thing, or the stylized ribbons
 * of photographs and games.
 */
public enum AuroraStyle implements ITranslatedEnum {
    CLASSIC("dsurround.config.auroraStyle.classic"),
    REALISTIC("dsurround.config.auroraStyle.realistic"),
    CINEMATIC("dsurround.config.auroraStyle.cinematic");

    private final String translationKey;

    AuroraStyle(String translationKey) {
        this.translationKey = translationKey;
    }

    @Override
    public String translationKey() {
        return this.translationKey;
    }
}

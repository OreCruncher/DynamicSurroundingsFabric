// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import org.orecruncher.dsurround.lib.config.ITranslatedEnum;

/**
 * How rainbows look: colors worked out from sunlight through raindrops, or flat stripes in blocks, to suit the game.
 */
public enum RainbowStyle implements ITranslatedEnum {
    REALISTIC("dsurround.config.rainbowStyle.realistic"),
    BLOCKY("dsurround.config.rainbowStyle.blocky");

    private final String translationKey;

    RainbowStyle(String translationKey) {
        this.translationKey = translationKey;
    }

    @Override
    public String translationKey() {
        return this.translationKey;
    }
}

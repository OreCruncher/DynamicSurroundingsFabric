// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import org.orecruncher.dsurround.lib.config.ITranslatedEnum;

/**
 * How rainbows, moonbows and halos look: flat stripes in blocks, to suit the game, or colors worked out from how light
 * passes through raindrops and ice.
 */
public enum SkyStyle implements ITranslatedEnum {
    BLOCKY("dsurround.config.skyStyle.blocky"),
    REALISTIC("dsurround.config.skyStyle.realistic");

    private final String translationKey;

    SkyStyle(String translationKey) {
        this.translationKey = translationKey;
    }

    @Override
    public String translationKey() {
        return this.translationKey;
    }
}

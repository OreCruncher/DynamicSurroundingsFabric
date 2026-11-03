// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.libraries;

import net.minecraft.world.level.Level;
import org.orecruncher.dsurround.config.DimensionInfo;

/**
 * Per-dimension settings from dimensions.json. Client thread only.
 */
public interface IDimensionLibrary extends ILibrary {

    /**
     * The settings for the level's dimension: the configured values over what the level itself reports (sea level,
     * height and so on). Built on first request and cached for the current world.
     */
    DimensionInfo getData(final Level world);
}

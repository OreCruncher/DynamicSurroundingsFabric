// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.weather;

/**
 * Whether a rainbow is in the sky: set each tick by {@code processing.RainbowHandler}, which decides and draws it, and
 * read by the script variable {@code weather.isRainbow}. Kept here, with what the weather is, so the scripts needn't
 * depend on the handler. Client thread only.
 */
public final class RainbowState {

    private boolean showing;

    /**
     * Whether a rainbow is showing (or fading), held by {@code /dsweather rainbow} or not
     */
    public boolean isShowing() {
        return this.showing;
    }

    public void setShowing(boolean showing) {
        this.showing = showing;
    }
}

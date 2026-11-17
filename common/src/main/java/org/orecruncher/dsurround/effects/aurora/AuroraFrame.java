// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.aurora;

/**
 * The classic aurora's shader values for a frame ({@code AuroraInfo}, aurora.fsh), worked out with no GPU in sight:
 * {@link AuroraRenderer} only packs them into its uniform block.
 *
 * @param palette  its colors, bottom to top
 * @param time     seconds, for the animation
 * @param strength how strongly it is drawn, 0 to 1: how strongly it is wanted, times the aurora's brightness
 */
public record AuroraFrame(AuroraPalette palette, float time, float strength) {

    /**
     * @param alpha how strongly it is wanted, 0 to 1
     */
    public static AuroraFrame of(Aurora aurora, float alpha, float time) {
        return new AuroraFrame(aurora.palette(), time, Math.clamp(alpha * aurora.brightness(), 0F, 1F));
    }
}

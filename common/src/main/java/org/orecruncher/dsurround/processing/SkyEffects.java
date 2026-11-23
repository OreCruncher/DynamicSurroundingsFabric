// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.compat.IrisCompat;

/**
 * What the effects drawn on the Overworld's sky (the aurora, rainbows, halos) share: where they can show at all, and how
 * much of them the fog lets through this frame. A new gate goes here, for all of them.
 */
final class SkyEffects {

    private SkyEffects() {
    }

    /**
     * Why a sky effect can't show at all, for the diagnostics, or null if it can
     *
     * @param enabled         whether its option is on
     * @param shaderAvailable whether its shader compiled and hasn't failed
     */
    static @Nullable String blocked(boolean enabled, boolean shaderAvailable, Level level) {
        if (!enabled)
            return "disabled";
        if (!shaderAvailable)
            return "shader unavailable or failed";
        if (IrisCompat.isShaderPackInUse())
            return "shader pack in use";
        if (level.dimensionType().skybox() != DimensionType.Skybox.OVERWORLD || level.dimensionType().hasCeiling())
            return "no sky";
        return null;
    }

    /**
     * How much of a sky effect shows this frame, 0 to 1: none without the Overworld's sky, from inside a fluid (vanilla
     * doesn't draw the sky from inside lava or powder snow, nor when blinded, and these aren't drawn from inside water
     * either) or with a shader pack; otherwise as much as the sky's fog lets through, which is set by now for the frame,
     * with the mod's own thickening (see {@link AuroraHandler#fogVisibility})
     */
    static float visibility(Level level, FogHandler fogHandler) {
        if (level.dimensionType().skybox() != DimensionType.Skybox.OVERWORLD)
            return 0F;
        if (GameUtils.getMC().gameRenderer.mainCamera().getFluidInCamera() != FogType.NONE)
            return 0F;
        if (IrisCompat.isShaderPackInUse())
            return 0F;
        var renderDistance = GameUtils.getMC().options.getEffectiveRenderDistance() * 16F;
        return AuroraHandler.fogVisibility(fogHandler.skyFogEnd(), renderDistance);
    }
}

// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects;

import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * The projection the world is being drawn with this frame, view bobbing and the nausea warp included, for effects that
 * turn pixels back into directions. {@code GameRenderer.renderLevel} works it out and hands it straight to a GPU buffer,
 * so it is kept here on its way there ({@code MixinGameRenderer}). Render thread only.
 */
public final class LevelProjection {

    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f ROTATION = new Matrix4f();
    private static boolean captured;

    private LevelProjection() {
    }

    public static void capture(Matrix4fc projection) {
        PROJECTION.set(projection);
        captured = true;
    }

    /**
     * The projection. Without the hook, the camera's own, which has no view bobbing: the effects still line up, but for
     * a small swing as the player walks.
     */
    public static Matrix4fc get() {
        if (!captured) {
            var camera = Minecraft.getInstance().gameRenderer.mainCamera();
            camera.getViewRotationProjectionMatrix(PROJECTION).mul(camera.getViewRotationMatrix(ROTATION).invert());
        }
        return PROJECTION;
    }
}

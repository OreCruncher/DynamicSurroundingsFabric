// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.effects.LevelProjection;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    /**
     * Starts building the frame's rain and snow on another thread as the level starts to be drawn, the camera set for
     * the frame (by update and extract, before render), so it is ready by the weather's pass (see DropMeshes).
     * Optional: without it they are built in that pass, as they were.
     */
    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), require = 0)
    private void dsurround$beginLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        var camera = ((GameRenderer) (Object) this).mainCamera();
        MixinHelpers.PRECIPITATION_AHEAD_GUARD.run(() -> MixinHelpers.precipitation().beginFrame(camera, deltaTracker.getGameTimeDeltaPartialTick(false)));
    }

    /**
     * Keeps the level's projection on its way to its GPU buffer, for what needs it in Java (see LevelProjection).
     * Optional: without it those use the camera's, without view bobbing.
     */
    @ModifyArg(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            require = 0)
    private Matrix4f dsurround$levelProjection(Matrix4f projection) {
        LevelProjection.capture(projection);
        return projection;
    }
}

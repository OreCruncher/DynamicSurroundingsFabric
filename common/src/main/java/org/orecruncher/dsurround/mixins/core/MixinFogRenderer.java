// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// A priority past the default (1000), so that our handler at the return of setupFog is applied after other mods' there,
// and runs after theirs: a distant terrain mod may stretch the fog at that point on Fabric (on NeoForge, in an event fired before it)
@Mixin(value = FogRenderer.class, priority = 1100)
public class MixinFogRenderer {

    // The fog's color while it is tinted; the color is set up on the render thread only
    @Unique
    private static final float[] dsurround$fogColor = new float[3];

    /**
     * Tints the fog's color, after the game and (on NeoForge) any mod has set it. The screen is cleared with the fog's
     * color, so that follows. Only in the open air: under water or in lava the fog is the fluid's.
     */
    @Inject(method = "computeFogColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IFLorg/joml/Vector4f;)V", at = @At("TAIL"), require = 0)
    private void dsurround$tintFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector4f color, CallbackInfo ci) {
        if (camera.getFluidInCamera() != FogType.NONE)
            return;
        var rgb = dsurround$fogColor;
        rgb[0] = color.x;
        rgb[1] = color.y;
        rgb[2] = color.z;
        // A color half tinted when it failed is left out: the game's stands
        if (MixinHelpers.FOG_TINT_GUARD.run(() -> MixinHelpers.fogHandler().tintFogColor(rgb, partialTick)))
            color.set(rgb[0], rgb[1], rgb[2], color.w);
    }

    /**
     * Once the game and every other mod have set the frame's fog: a mod that draws terrain past the render distance
     * has stretched the fog out to it by then, and our fog, held back while it does, is added on top (see
     * ExtendedViewFog). Without such a mod nothing is held back and nothing changes here.
     */
    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;", at = @At("RETURN"), require = 0)
    private void dsurround$finishFog(Camera camera, int renderDistance, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        var data = cir.getReturnValue();
        if (data != null)
            MixinHelpers.FOG_FINISH_GUARD.run(() -> MixinHelpers.fogHandler().finishFog(data, renderDistance * 16F));
    }
}

// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public class MixinCloudRenderer {

    /**
     * The mod's clouds in place of vanilla's. As vanilla's are prepared, the color and camera they would have had are
     * kept for ours, drawn once the pass vanilla's would have been drawn in is closed (see CloudHandler), and vanilla's
     * are left out. When ours can't be drawn, vanilla's are, as they would have been.
     */
    @Inject(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void dsurround$cloudsPrepared(int color, CloudStatus cloudStatus, float bottomY, int range, Vec3 cameraPosition, long gameTime,
                                          float partialTicks, CallbackInfo ci) {
        if (MixinHelpers.CLOUDS_GUARD.get(() -> MixinHelpers.clouds().prepare(color, cameraPosition, partialTicks), false))
            ci.cancel();
    }

    @Inject(method = {
            "render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
            "renderOit(Lnet/minecraft/client/CloudStatus;Lnet/minecraft/client/renderer/oit/OitStage;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;)V"
    }, at = @At("HEAD"), cancellable = true, require = 0)
    private void dsurround$clouds(CallbackInfo ci) {
        if (MixinHelpers.CLOUDS_GUARD.get(() -> MixinHelpers.clouds().replacesClouds(), false))
            ci.cancel();
    }
}

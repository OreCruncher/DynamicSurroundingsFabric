package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
    private static void dsurround_renderFog(Camera camera, FogRenderer.FogMode fogMode, float f, boolean bl, float g, CallbackInfo ci, FogType fogType, Entity entity, FogRenderer.FogData fogData) {

        if (fogData.mode != FogRenderer.FogMode.FOG_TERRAIN || fogType != FogType.NONE)
            return;

        ClientEventHooks.FOG_RENDER_EVENT.raise().onRenderFog(fogData, f, g);
    }
}

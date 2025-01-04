package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V", at = @At("RETURN"))
    private static void dsurround_renderFog(Camera camera, FogRenderer.FogMode fogMode, float f, boolean bl, float g, CallbackInfo ci, @Local FogType fogType, @Local FogRenderer.FogData fogData) {

        if (fogData.mode != FogRenderer.FogMode.FOG_TERRAIN || fogType != FogType.NONE)
            return;

        // At this point, Minecraft has already configured fog. It's possible that another
        // mixin fired and configured as well. We cannot trust the state of fogData, so
        // we interrogate the shader directly to see what was configured. (Nostalgic Tweaks
        // uses this approach.)
        var data = new FogRenderer.FogData(fogData.mode);
        data.start = RenderSystem.getShaderFogStart();
        data.end = RenderSystem.getShaderFogEnd();
        data.shape = RenderSystem.getShaderFogShape();

        ClientEventHooks.FOG_RENDER_EVENT.raise().onRenderFog(data, f, g);
    }
}

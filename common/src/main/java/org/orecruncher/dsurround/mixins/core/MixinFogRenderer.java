package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {


    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;Lorg/joml/Vector4f;FZF)Lnet/minecraft/client/renderer/FogParameters;", at =@At(value="INVOKE", target = "Lnet/minecraft/client/renderer/FogParameters;<init>(FFLcom/mojang/blaze3d/shaders/FogShape;FFFF)V", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILEXCEPTION, cancellable = true)
    private static void dsurround_renderFog(Camera camera, FogRenderer.FogMode fogMode, Vector4f vector4f, float f, boolean bl, float g, CallbackInfoReturnable<FogParameters> cir, FogType fogType, Entity entity, FogRenderer.FogData fogData) {

        if (fogMode != FogRenderer.FogMode.FOG_TERRAIN)
            return;

        ClientEventHooks.FOG_RENDER_EVENT.raise().onRenderFog(fogData, f, g);
        cir.setReturnValue(new FogParameters(fogData.start, fogData.end, fogData.shape, vector4f.x, vector4f.y, vector4f.z, vector4f.w));
    }
}

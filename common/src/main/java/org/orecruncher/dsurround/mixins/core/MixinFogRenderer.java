package org.orecruncher.dsurround.mixins.core;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.processing.fog.HolisticFogRangeCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Unique
    private static final HolisticFogRangeCalculator dsurround_fogCalculator = ContainerManager.resolve(HolisticFogRangeCalculator.class);

    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
    private static void dsurround_setupFog(Camera camera, FogRenderer.FogMode fogMode, float f, boolean bl, float g, CallbackInfo ci, FogType fogType, Entity entity, FogRenderer.FogData fogData) {

        if (fogData.mode != FogRenderer.FogMode.FOG_TERRAIN || fogType != FogType.NONE)
            return;

        var result = dsurround_fogCalculator.render(fogData, f, g);
        RenderSystem.setShaderFogStart(result.start);
        RenderSystem.setShaderFogEnd(result.end);
        RenderSystem.setShaderFogShape(result.shape);
    }
}

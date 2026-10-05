package org.orecruncher.dsurround.mixins.core;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.orecruncher.dsurround.eventing.ISkyRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class MixinSkyRenderer {

    /**
     * The sun, moon and stars have been drawn; only drawn for an Overworld-like sky. Anything drawn now is behind the
     * world, as part of the sky. The model view matrix is back to the camera's rotation here.
     */
    @Inject(method = "renderSunMoonAndStars(Lcom/mojang/blaze3d/vertex/PoseStack;FFFLnet/minecraft/world/level/MoonPhase;FF)V", at = @At("TAIL"))
    private void dsurround$renderSky(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        var partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ISkyRender.EVENT.invoker().onRenderSky(RenderSystem.getModelViewMatrixCopy(), partialTick, starBrightness, rainBrightness);
    }
}

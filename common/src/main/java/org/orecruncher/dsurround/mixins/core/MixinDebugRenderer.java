package org.orecruncher.dsurround.mixins.core;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.orecruncher.dsurround.gui.overlay.EffectSystemsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the diagnostics overlay's in-world view of tracked effects along with vanilla's debug renderers: during world
 * rendering, with the camera position and buffers those use. The same on both platforms, so no render event of either
 * is needed.
 */
@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;DDD)V", at = @At("RETURN"))
    private void dsurround$renderTrackedEffects(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ, CallbackInfo ci) {
        EffectSystemsRenderer.render(poseStack, bufferSource, camX, camY, camZ);
    }
}

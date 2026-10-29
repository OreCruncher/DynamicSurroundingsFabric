package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.orecruncher.dsurround.gui.overlay.EffectSystemsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the diagnostics overlay's in-world view of tracked effects to the gizmos vanilla's debug renderers emit each
 * frame. The same on both platforms, so no render event of either is needed.
 */
@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {

    @Inject(method = "emitGizmos(Lnet/minecraft/client/renderer/culling/Frustum;DDDF)V", at = @At("TAIL"))
    private void dsurround$emitTrackedEffects(Frustum frustum, double camX, double camY, double camZ, float partialTicks, CallbackInfo ci) {
        EffectSystemsRenderer.emit(camX, camY, camZ);
    }
}

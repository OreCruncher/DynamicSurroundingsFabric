package org.orecruncher.dsurround.mixins.core;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves out vanilla's drawing of a lightning bolt the mod draws as a strike of its own (see LightningHandler). The
 * bolt is drawn from its render state, which has the bolt's seed but not the bolt: the mod knows its bolts by seed.
 * Optional: should it not apply, both would be drawn, so the hook that hands the mod the bolts is optional too.
 */
@Mixin(LightningBoltRenderer.class)
public class MixinLightningBoltRenderer {

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LightningBoltRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void dsurround$vanillaBolt(LightningBoltRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        long seed = state.seed;
        if (MixinHelpers.LIGHTNING_GUARD.get(() -> MixinHelpers.lightning().drawsBolt(seed), false))
            ci.cancel();
    }
}

package org.orecruncher.dsurround.mixins.core;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.world.entity.LightningBolt;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves out vanilla's drawing of a lightning bolt the mod draws as a strike of its own (see LightningHandler).
 * Optional: should it not apply, both would be drawn, so the hook that hands the mod the bolts is optional too.
 */
@Mixin(LightningBoltRenderer.class)
public class MixinLightningBoltRenderer {

    @Inject(method = "render(Lnet/minecraft/world/entity/LightningBolt;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void dsurround$vanillaBolt(LightningBolt bolt, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, CallbackInfo ci) {
        if (MixinHelpers.LIGHTNING_GUARD.get(() -> MixinHelpers.lightning().drawsBolt(bolt), false))
            ci.cancel();
    }
}

package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hooks the drawing of each layer of quad particles, for soft particles (see SoftParticles and
 * MixinParticleFeatureRenderer).
 */
@Mixin(QuadParticleRenderState.class)
public class MixinQuadParticleRenderState {

    /**
     * As each particle layer's pipeline is set: binds the depth copy for a soft pipeline, or swaps the pipeline for
     * its vanilla equivalent if there is no copy this frame.
     */
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    private void dsurround$setPipeline(RenderPass pass, RenderPipeline pipeline, Operation<Void> original) {
        var chosen = SoftParticles.choosePipeline(pipeline);
        original.call(pass, chosen);
        SoftParticles.bind(pass, chosen);
        SoftParticles.setCurrentPipeline(chosen);
    }

    /**
     * A soft layer drawn in two passes with Fabulous graphics: its depth first, then (as the layer's own draw) its
     * color; see SoftParticles. The color pipeline was set and bound just before.
     */
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V"))
    private void dsurround$drawLayer(RenderPass pass, int baseVertex, int firstIndex, int indexCount, int instanceCount, Operation<Void> original) {
        var color = SoftParticles.currentPipeline();
        var depth = color == null ? null : SoftParticles.depthPassFor(color);
        if (depth != null) {
            pass.setPipeline(depth);
            SoftParticles.bind(pass, depth);
            original.call(pass, baseVertex, firstIndex, indexCount, instanceCount);
            pass.setPipeline(color);
            SoftParticles.bind(pass, color);
        }
        original.call(pass, baseVertex, firstIndex, indexCount, instanceCount);
    }
}

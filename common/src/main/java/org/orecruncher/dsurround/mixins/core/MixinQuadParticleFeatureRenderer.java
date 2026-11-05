// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import org.orecruncher.dsurround.effects.particles.PointLights;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.orecruncher.dsurround.effects.particles.Streaks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


/**
 * Hooks the drawing of quad particles for soft particles, which read the scene's depth.
 */
@Mixin(QuadParticleFeatureRenderer.class)
public class MixinQuadParticleFeatureRenderer {

    /**
     * As each particle layer's pipeline is chosen: swaps it for its vanilla equivalent if there is no depth copy, and
     * binds the copy for a soft pipeline, which reads it.
     */
    @WrapOperation(method = "drawLayers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getCompiledPipeline(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;"))
    private static CompiledRenderPipeline dsurround$choosePipeline(RenderPipeline pipeline, Operation<CompiledRenderPipeline> original, @Local(argsOnly = true) RenderPass pass) {
        var chosen = SoftParticles.choosePipeline(pipeline);
        SoftParticles.bind(pass, chosen);
        return original.call(chosen);
    }

    @Inject(method = "finishExecute(Lnet/minecraft/client/renderer/feature/FeatureFrameContext;)V", at = @At("TAIL"))
    private void dsurround$afterFrame(FeatureFrameContext context, CallbackInfo ci) {
        Streaks.endFrame();
        PointLights.endFrame();
    }
}

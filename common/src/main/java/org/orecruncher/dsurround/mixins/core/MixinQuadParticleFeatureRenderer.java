package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import org.orecruncher.dsurround.effects.particles.FireflyLights;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Hooks the drawing of quad particles for soft particles and firefly lights, both of which read the scene's depth.
 */
@Mixin(QuadParticleFeatureRenderer.class)
public class MixinQuadParticleFeatureRenderer {

    /**
     * Before translucent particles are drawn, and outside their render pass: copy the scene's depth, and draw the
     * firefly lights, so the fireflies drawn next are on top of their own light.
     */
    @Inject(method = "executeGroup(Lnet/minecraft/client/renderer/feature/FeatureFrameContext;ILjava/util/List;Z)V", at = @At("HEAD"))
    private void dsurround$beforeGroup(FeatureFrameContext context, int groupIndex, List<QuadParticleFeatureRenderer.Submit> submits, boolean strictlyOrdered, CallbackInfo ci) {
        if (submits.isEmpty() || !submits.getFirst().translucent())
            return;
        // The same target vanilla draws translucent particles to
        var minecraft = Minecraft.getInstance();
        var particleTarget = minecraft.levelRenderer.particlesTarget();
        var target = particleTarget != null ? particleTarget : minecraft.gameRenderer.mainRenderTarget();
        SoftParticles.captureSceneDepth(target);
        FireflyLights.draw(target);
    }

    /**
     * As each particle layer's pipeline is set: binds the depth copy for a soft pipeline, or swaps the pipeline for
     * its vanilla equivalent if there is no copy this frame.
     */
    @WrapOperation(method = "drawLayers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    private static void dsurround$setPipeline(RenderPass pass, RenderPipeline pipeline, Operation<Void> original) {
        var chosen = SoftParticles.choosePipeline(pipeline);
        original.call(pass, chosen);
        SoftParticles.bind(pass, chosen);
    }

    @Inject(method = "finishExecute(Lnet/minecraft/client/renderer/feature/FeatureFrameContext;)V", at = @At("TAIL"))
    private void dsurround$afterFrame(FeatureFrameContext context, CallbackInfo ci) {
        SoftParticles.endFrame();
    }
}

package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.ParticleFeatureRenderer;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import org.orecruncher.dsurround.effects.particles.PointLights;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.orecruncher.dsurround.effects.particles.Streaks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Hooks the drawing of particles for soft particles and firefly lights, both of which read the scene's depth. Each
 * group of particles is drawn in a render pass of its own: to the main target, the translucent particles with the
 * rest; or with Fabulous graphics, the translucent ones in a second pass, to the particles' target. The layers' draws
 * are hooked in MixinQuadParticleRenderState.
 */
@Mixin(ParticleFeatureRenderer.class)
public class MixinParticleFeatureRenderer {

    /**
     * Before the pass the translucent particles are drawn in, and outside it (a depth copy can't be made in a pass):
     * copy the scene's depth, and draw the point lights, so the fireflies and flames drawn next are on top of their
     * own light, and the streaks of sparks and molten droplets.
     */
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;"))
    private RenderPass dsurround$beforeTranslucent(CommandEncoder encoder, Supplier<String> label, GpuTextureView color, OptionalInt clearColor,
                                                   GpuTextureView depth, OptionalDouble clearDepth, Operation<RenderPass> original,
                                                   @Local QuadParticleRenderState.PreparedBuffers prepared) {
        // The same target vanilla draws translucent particles to
        var minecraft = Minecraft.getInstance();
        var particleTarget = minecraft.levelRenderer.getParticlesTarget();
        var target = particleTarget != null ? particleTarget : minecraft.getMainRenderTarget();
        if (color == target.getColorTextureView() && hasTranslucent(prepared)) {
            SoftParticles.captureSceneDepth(target);
            PointLights.draw(target, prepared.dynamicTransforms());
            Streaks.draw(target, prepared.dynamicTransforms());
        }
        return original.call(encoder, label, color, clearColor, depth, clearDepth);
    }

    private static boolean hasTranslucent(QuadParticleRenderState.PreparedBuffers prepared) {
        for (var layer : prepared.layers().keySet())
            if (layer.translucent())
                return true;
        return false;
    }

    @Inject(method = "endFrame", at = @At("TAIL"))
    private void dsurround$afterFrame(CallbackInfo ci) {
        SoftParticles.endFrame();
        Streaks.endFrame();
        PointLights.endFrame();
    }
}

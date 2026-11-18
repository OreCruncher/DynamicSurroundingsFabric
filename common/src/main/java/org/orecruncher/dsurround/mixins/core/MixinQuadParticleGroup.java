// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.orecruncher.dsurround.profiling.ParticleSamples;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(QuadParticleGroup.class)
public class MixinQuadParticleGroup {

    /**
     * Times writing each particle's quad for the frame while /dsprofile records (see ParticleSamples). Render thread.
     * Optional: without it, recordings have no particle times.
     */
    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/Camera;F)Lnet/minecraft/client/renderer/state/level/ParticleGroupRenderState;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/SingleQuadParticle;extract(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lnet/minecraft/client/Camera;F)V"),
            require = 0)
    private void dsurround$timeExtract(SingleQuadParticle particle, QuadParticleRenderState state, Camera camera, float partialTick,
                                       Operation<Void> original) {
        if (!ParticleSamples.isRecording()) {
            original.call(particle, state, camera, partialTick);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(particle, state, camera, partialTick);
        } finally {
            ParticleSamples.extracted(particle, System.nanoTime() - start);
        }
    }
}

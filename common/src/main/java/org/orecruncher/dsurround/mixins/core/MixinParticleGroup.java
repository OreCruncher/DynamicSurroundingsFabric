// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import org.orecruncher.dsurround.profiling.ParticleSamples;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ParticleGroup.class)
public class MixinParticleGroup {

    /**
     * Times each particle's tick while /dsprofile records (see ParticleSamples). Any thread: mods tick particles on
     * several at once. Optional: without it, recordings have no particle times.
     */
    @WrapOperation(method = "tickParticle(Lnet/minecraft/client/particle/Particle;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;tick()V"), require = 0)
    private void dsurround$timeTick(Particle particle, Operation<Void> original) {
        if (!ParticleSamples.isRecording()) {
            original.call(particle);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(particle);
        } finally {
            ParticleSamples.ticked(particle, System.nanoTime() - start);
        }
    }
}

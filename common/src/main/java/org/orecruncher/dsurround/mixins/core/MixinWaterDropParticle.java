// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.WaterDropParticle;
import org.orecruncher.dsurround.effects.WaterRippleHandler;
import org.orecruncher.dsurround.lib.GameUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spray (a waterfall's, a splash's) that falls back onto the water leaves a ripple, as rain does.
 */
@Mixin(WaterDropParticle.class)
public abstract class MixinWaterDropParticle extends Particle {

    protected MixinWaterDropParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    /**
     * Its last removal: it has come down into a block or a fluid
     */
    @Inject(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/WaterDropParticle;remove()V", ordinal = 2), require = 0)
    private void dsurround$landed(CallbackInfo ci) {
        WaterRippleHandler.createSprayRipple(this.level, this.x, this.y, this.z).ifPresent(GameUtils.getParticleManager()::add);
    }
}

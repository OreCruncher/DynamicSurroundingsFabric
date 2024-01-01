package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractArrow.class)
public abstract class MixinEntityArrow {

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;isCritArrow()Z"))
    private boolean dsurround_isCriticalCheck(AbstractArrow instance, Operation<Boolean> original) {
        if (MixinHelpers.particleTweaksConfig.suppressProjectileParticleTrails)
            return false;
        return original.call(instance);
    }
}
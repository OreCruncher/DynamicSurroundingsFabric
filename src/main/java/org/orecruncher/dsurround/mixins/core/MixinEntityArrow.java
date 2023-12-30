package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PersistentProjectileEntity.class)
public abstract class MixinEntityArrow {

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/projectile/PersistentProjectileEntity;isCritical()Z"))
    private boolean dsurround_isCriticalCheck(PersistentProjectileEntity instance, Operation<Boolean> original) {
        if (MixinHelpers.particleTweaksConfig.suppressProjectileParticleTrails)
            return false;
        return original.call(instance);
    }
}
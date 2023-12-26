package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PersistentProjectileEntity.class)
public abstract class MixinEntityArrow {

    private static final Configuration.ParticleTweaks dsurround_config = ContainerManager.resolve(Configuration.ParticleTweaks.class);

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/projectile/PersistentProjectileEntity;isCritical()Z"))
    private boolean dsurround_isCriticalCheck(PersistentProjectileEntity instance, Operation<Boolean> original) {
        if (dsurround_config.suppressProjectileParticleTrails)
            return false;
        return original.call(instance);
    }
}
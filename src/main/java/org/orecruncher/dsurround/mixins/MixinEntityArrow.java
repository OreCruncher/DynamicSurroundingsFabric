package org.orecruncher.dsurround.mixins;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.orecruncher.dsurround.Client;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PersistentProjectileEntity.class)
public abstract class MixinEntityArrow {

    @Shadow
    public boolean isCritical() {
        return false;
    }

    @Redirect(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/projectile/PersistentProjectileEntity;isCritical()Z"))
    private boolean isCriticalCheck(PersistentProjectileEntity instance) {
        return instance.isCritical() && Client.Config.particleTweaks.showProjectileTrails;
    }
}
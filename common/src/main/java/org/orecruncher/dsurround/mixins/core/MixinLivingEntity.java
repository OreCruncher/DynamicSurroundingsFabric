package org.orecruncher.dsurround.mixins.core;

import net.minecraft.world.entity.LivingEntity;
import org.orecruncher.dsurround.effects.entity.DeathPuffs;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla's white poof where a mob's body vanishes with dark mist (see {@link DeathPuffs}). Optional: without
 * it, vanilla's poof stays.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Inject(method = "makePoofParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void dsurround$deathPuffs(CallbackInfo ci) {
        try {
            if (DeathPuffs.replace((LivingEntity) (Object) this))
                ci.cancel();
        } catch (Throwable t) {
            MixinHelpers.DEATH_PUFF_GUARD.failed(t);
        }
    }
}

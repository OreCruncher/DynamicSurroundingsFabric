package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.orecruncher.dsurround.effects.entity.BurningEntities;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Leaves vanilla's fire sheets off a burning entity whose fire the mod draws (see
 * {@link org.orecruncher.dsurround.effects.entity.BurningEffect}). Optional: other mods replace entity rendering, and
 * without this hook the mod doesn't draw fire at all, so vanilla's stays.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;displayFireAnimation()Z"), require = 0)
    private boolean dsurround$vanillaFire(Entity entity, Operation<Boolean> original) {
        boolean burning = original.call(entity);
        if (!burning)
            return false;
        try {
            return !BurningEntities.drawsFireOf(entity.getId());
        } catch (Throwable t) {
            MixinHelpers.VANILLA_FIRE_GUARD.failed(t);
            return true;
        }
    }
}

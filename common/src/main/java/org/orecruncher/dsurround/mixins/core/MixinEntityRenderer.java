package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.orecruncher.dsurround.effects.entity.BurningEntities;
import org.orecruncher.dsurround.effects.entity.BurningLight;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Leaves vanilla's fire off a burning entity whose fire the mod draws (see
 * {@link org.orecruncher.dsurround.effects.entity.BurningEffect}), and adds that fire's light, as the entity's render
 * state is extracted for the frame: the state's {@code displayFireAnimation} is what has vanilla submit its flames.
 * Optional: other mods replace entity rendering, and without this hook the mod doesn't draw fire at all, so vanilla's
 * stays.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;displayFireAnimation()Z"), require = 0)
    private boolean dsurround$vanillaFire(Entity entity, Operation<Boolean> original, @Local(argsOnly = true) float partialTick) {
        boolean burning = original.call(entity);
        if (!burning)
            return false;
        boolean ours;
        try {
            ours = BurningEntities.drawsFireOf(entity.getId());
        } catch (Throwable t) {
            MixinHelpers.VANILLA_FIRE_GUARD.failed(t);
            return true;
        }
        if (!ours)
            return true;
        // Should the light fail, the fire is still the mod's: vanilla's stays off
        try {
            BurningLight.add(entity, partialTick);
        } catch (Throwable t) {
            MixinHelpers.VANILLA_FIRE_GUARD.failed(t);
        }
        return false;
    }
}

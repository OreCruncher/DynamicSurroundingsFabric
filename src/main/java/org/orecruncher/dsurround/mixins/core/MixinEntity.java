package org.orecruncher.dsurround.mixins.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.orecruncher.dsurround.tags.EntityEffectTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Unique
    private static final double DSURROUND_MAX_ACCENT_RANGE = 16.0 * 16.0;

    @Shadow
    private Level level;

    @Inject(method = "walkingStepSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("TAIL"))
    public void dsurround_playStepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
        // Only want to enable eventing if accents are enabled
        if (MixinHelpers.footstepAccentsConfig.enableAccents && this.level.isClientSide) {
            var self = (Entity) ((Object) this);

            // Is the entity in range?  If not, avoid generating an event
            if (GameUtils.getPlayer().orElseThrow().distanceToSqr(self) > DSURROUND_MAX_ACCENT_RANGE)
                return;

            // Lastly, the entity has to be tagged
            if (MixinHelpers.TAG_LIBRARY.is(EntityEffectTags.BRUSH_STEP, self.getType())) {
                ClientEventHooks.ENTITY_STEP_EVENT.raise().onStep(self, pos, state);
            }
        }
    }
}

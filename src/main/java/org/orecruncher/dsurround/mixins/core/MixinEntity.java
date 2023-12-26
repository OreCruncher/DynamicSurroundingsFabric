package org.orecruncher.dsurround.mixins.core;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.tags.EntityEffectTags;
import org.orecruncher.dsurround.tags.TagHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class MixinEntity {

    @Unique
    private static final double DSURROUND_MAX_ACCENT_RANGE = 16.0 * 16.0;

    @Shadow
    private World world;

    @Inject(method = "playStepSound(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V", at = @At("TAIL"))
    public void dsurround_playStepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
        // Only want to enable eventing if accents are enabled
        if (Client.Config.footstepAccents.enableAccents && world.isClient) {
            var current = (Entity) ((Object) this);

            // Is the entity in range?  If not avoid generating an event
            if (GameUtils.getPlayer().orElseThrow().squaredDistanceTo(current) > DSURROUND_MAX_ACCENT_RANGE)
                return;

            // Lastly, the entity has to be tagged
            if (TagHelpers.isIn(EntityEffectTags.BRUSH_STEP, current.getType())) {
                var event = new ClientEventHooks.EntityStepEvent(current, pos, state);
                ClientEventHooks.ENTITY_STEP_EVENT.raise(event);
            }
        }
    }
}

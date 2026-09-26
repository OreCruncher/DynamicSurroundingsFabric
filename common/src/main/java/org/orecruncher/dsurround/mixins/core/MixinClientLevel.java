package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.eventing.handlers.BlockUpdateHandler;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class MixinClientLevel {

    /**
     * Tap into block state change detection in the World instance.  Need to be careful to only get updates to
     * a world that is client side.  Server side is a don't care.
     */
    @Inject(method = "sendBlockUpdated(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V", at = @At("RETURN"))
    public void dsurround$setBlocksDirty(BlockPos pos, BlockState old, BlockState current, int updateFlags, CallbackInfo ci) {
        ReflectionHelper.cast(this, ClientLevel.class)
            .ifPresent(level -> BlockUpdateHandler.blockPositionUpdate(level, pos, old, current));
    }
}

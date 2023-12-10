package org.orecruncher.dsurround.mixins.core;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.orecruncher.dsurround.eventing.handlers.BlockUpdateHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public class MixinWorld {

    /**
     * Tap into block state change detection in the World instance.  Need to be careful to only get updates to
     * a world that is client side.  Server side is a don't care.
     */
    @Inject(method = "onBlockChanged(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;)V", at = @At("HEAD"))
    public void dsurround_onBlockChanged(BlockPos pos, BlockState oldBlock, BlockState newBlock, CallbackInfo ci) {
        // World is AutoClosable and tooling thinks this is a leak...
        var world = ((World) (Object) this);
        if (world.isClient()) {
            BlockUpdateHandler.blockPositionUpdate(pos);
        }
    }
}

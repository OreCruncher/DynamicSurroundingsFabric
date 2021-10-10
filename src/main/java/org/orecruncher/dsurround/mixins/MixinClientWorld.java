package org.orecruncher.dsurround.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.eventing.handlers.BlockUpdateHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class MixinClientWorld {

    @Inject(
            method = "updateListeners(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;I)V",
            at = @At("HEAD")
    )
    private void updateListener(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        BlockUpdateHandler.blockPositionUpdate(pos);
    }
}

package org.orecruncher.dsurround.mixins.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.eventing.handlers.BlockUpdateHandler;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public class MixinWorld {

    /**
     * Tap into block state change detection in the World instance.  Need to be careful to only get updates to
     * a world that is client side.  Server side is a don't care.
     */
    @Inject(method = "onBlockStateChange(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("HEAD"))
    public void dsurround$onBlockChanged(BlockPos pos, BlockState oldBlock, BlockState newBlock, CallbackInfo ci) {
        // The World is AutoClosable and tooling thinks this is a leak...
        var self = ReflectionHelper.cast(this, Level.class);
        self.ifPresent(level -> {
            if (level.isClientSide()) {
                BlockUpdateHandler.blockPositionUpdate(self.get(), pos, oldBlock, newBlock);
            }
        });
    }
}

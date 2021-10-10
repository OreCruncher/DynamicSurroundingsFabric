package org.orecruncher.dsurround.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.eventing.handlers.BlockUpdateHandler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientWorld.class)
public class MixinClientWorld {

    /**
     * This will introduce a class override so that the position update hook can
     * be invoked.  ClientWorld does not normally have this defined.  May change to
     * hook in World instead.
     */
    public boolean setBlockState(BlockPos pos, BlockState state, int flags) {
        var result = ((ClientWorld)(Object)this).setBlockState(pos, state, flags, 512);
        BlockUpdateHandler.blockPositionUpdate(pos);
        return result;
    }
}

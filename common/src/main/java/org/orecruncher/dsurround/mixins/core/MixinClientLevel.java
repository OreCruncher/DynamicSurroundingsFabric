package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.eventing.IChunkLoad;
import org.orecruncher.dsurround.eventing.handlers.BlockUpdateHandler;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
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
        BlockUpdateHandler.blockPositionUpdate((ClientLevel) (Object) this, pos, old, current);
    }

    /**
     * ClientChunkCache calls this once a chunk's data from the server has been loaded, so the chunk's blocks can
     * be read by the time it returns.
     */
    @Inject(method = "onChunkLoaded(Lnet/minecraft/world/level/ChunkPos;)V", at = @At("TAIL"))
    public void dsurround$onChunkLoaded(ChunkPos chunkPos, CallbackInfo ci) {
        IChunkLoad.EVENT.invoker().onChunkLoad((ClientLevel) (Object) this, chunkPos);
    }

    /**
     * A lightning bolt arriving from the server: the mod draws it as a strike of its own, if its lightning is on (see
     * LightningHandler). Optional: without it the game draws its own bolts.
     */
    @Inject(method = "addEntity(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"), require = 0)
    private void dsurround$lightningBolt(Entity entity, CallbackInfo ci) {
        if (entity instanceof LightningBolt bolt)
            MixinHelpers.LIGHTNING_GUARD.run(() -> MixinHelpers.lightning().onBolt(bolt));
    }
}

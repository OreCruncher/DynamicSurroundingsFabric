package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import org.orecruncher.dsurround.eventing.IChunkLoad;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class MixinClientLevel {

    /**
     * ClientChunkCache calls this once a chunk's data from the server has been loaded, so the chunk's blocks can
     * be read by the time it returns.
     */
    @Inject(method = "onChunkLoaded(Lnet/minecraft/world/level/ChunkPos;)V", at = @At("TAIL"))
    public void dsurround$onChunkLoaded(ChunkPos chunkPos, CallbackInfo ci) {
        ReflectionHelper.cast(this, ClientLevel.class)
                .ifPresent(level -> IChunkLoad.EVENT.invoker().onChunkLoad(level, chunkPos));
    }
}

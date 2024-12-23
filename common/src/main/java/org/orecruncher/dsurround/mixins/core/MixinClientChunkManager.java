package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.multiplayer.ClientChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientChunkCache.class)
public interface MixinClientChunkManager {

    @Accessor("storage")
    ClientChunkCache.Storage dsurround_getClientChunkMap();
}

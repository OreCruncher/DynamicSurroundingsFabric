package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicReferenceArray;

@Mixin(ClientChunkCache.Storage.class)
public interface MixinClientChunkMap {

    @Accessor("chunks")
    AtomicReferenceArray<LevelChunk> dsurround_getChunks();
}

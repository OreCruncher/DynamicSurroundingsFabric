package org.orecruncher.dsurround.mixins.core;

import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.world.ClientChunkManager;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicReferenceArray;

@Mixin(ClientChunkManager.ClientChunkMap.class)
public interface MixinClientChunkMap {

    @Accessor("chunks")
    AtomicReferenceArray<WorldChunk> dsurround_getChunks();
}

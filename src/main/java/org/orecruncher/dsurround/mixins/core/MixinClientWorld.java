package org.orecruncher.dsurround.mixins.core;

import org.orecruncher.dsurround.mixinutils.IClientWorld;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Mixin(ClientWorld.class)
public class MixinClientWorld implements IClientWorld {

    @Final
    @Shadow
    private ClientChunkManager chunkManager;

    @Unique
    public Stream<WorldChunk> dsurround_getLoadedChunks() {
        var x = (MixinClientChunkManager) this.chunkManager;
        var chunkMap = x.dsurround_getClientChunkMap();
        var chunks = ((MixinClientChunkMap)((Object)chunkMap)).dsurround_getChunks();

        List<WorldChunk> resultChunks = new ArrayList<>();
        for (int i = 0; i < chunks.length(); i++) {
            var chunk = chunks.get(i);
            if (chunk != null)
                resultChunks.add(chunk);
            else {
                int q = 0;
            }
        }

        return resultChunks.stream();
    }
}

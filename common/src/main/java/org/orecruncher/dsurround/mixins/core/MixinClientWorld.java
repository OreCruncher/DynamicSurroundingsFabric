package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.orecruncher.dsurround.mixinutils.IClientWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Mixin(ClientLevel.class)
public class MixinClientWorld implements IClientWorld {

    @Final
    @Shadow
    private ClientChunkCache chunkSource;

    @Unique
    public Stream<LevelChunk> dsurround_getLoadedChunks() {
        var x = (MixinClientChunkManager) this.chunkSource;
        var chunkMap = x.dsurround_getClientChunkMap();
        var chunks = ((MixinClientChunkMap)((Object)chunkMap)).dsurround_getChunks();

        List<LevelChunk> resultChunks = new ArrayList<>();
        for (int i = 0; i < chunks.length(); i++) {
            var chunk = chunks.get(i);
            if (chunk != null)
                resultChunks.add(chunk);
        }

        return resultChunks.stream();
    }
}

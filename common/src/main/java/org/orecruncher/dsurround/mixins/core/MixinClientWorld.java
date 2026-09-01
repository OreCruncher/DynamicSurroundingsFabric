package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;
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
    public Stream<LevelChunk> dsurround$getLoadedChunks() {
        var manager = ReflectionHelper.cast(this.chunkSource, MixinClientChunkManager.class);
        if (manager.isPresent()) {
            var chunkMap = ReflectionHelper.cast(manager.get().dsurround$getClientChunkMap(), MixinClientChunkMap.class);
            if (chunkMap.isPresent()) {
                var chunks = chunkMap.get().dsurround$getChunks();
                List<LevelChunk> resultChunks = new ArrayList<>();
                for (int i = 0; i < chunks.length(); i++) {
                    var chunk = chunks.get(i);
                    if (chunk != null)
                        resultChunks.add(chunk);
                }
                return resultChunks.stream();
            }
        }

        return Stream.empty();
    }
}

package org.orecruncher.dsurround.mixinutils;

import net.minecraft.world.level.chunk.LevelChunk;

import java.util.stream.Stream;

public interface IClientWorld {
    Stream<LevelChunk> dsurround$getLoadedChunks();
}

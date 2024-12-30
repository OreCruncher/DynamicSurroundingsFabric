package org.orecruncher.dsurround.mixinutils;

import net.minecraft.world.level.chunk.LevelChunk;

import java.util.stream.Stream;

public interface IClientWorld {
    long dsurround_getWorldSeed();
    Stream<LevelChunk> dsurround_getLoadedChunks();
}

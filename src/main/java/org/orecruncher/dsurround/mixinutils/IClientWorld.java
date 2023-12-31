package org.orecruncher.dsurround.mixinutils;

import net.minecraft.world.chunk.WorldChunk;

import java.util.stream.Stream;

public interface IClientWorld {
    Stream<WorldChunk> dsurround_getLoadedChunks();
}

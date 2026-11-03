// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.mixinutils;

import net.minecraft.world.level.chunk.LevelChunk;

import java.util.stream.Stream;

public interface IClientWorld {
    Stream<LevelChunk> dsurround$getLoadedChunks();
}

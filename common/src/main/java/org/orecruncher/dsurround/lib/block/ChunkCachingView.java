// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A level's blocks and fluids for one thread's ray traces, read from the chunk the last read was in while the next is
 * in it too. {@code Level.getBlockState} looks the chunk up for every block, which was about a quarter of a sound's
 * trace; a ray walks through a chunk's blocks one after another, so most reads find the chunk already in hand. Reads
 * as the level does: void air and no fluid outside its bounds, an empty chunk where none is loaded.
 * <p>
 * Never its block entities, as {@link NoBlockEntityView}: on the client, asking for one from another thread can create
 * it (issue #166). Not thread safe: one per thread, attached to the level while tracing and detached after, so a kept
 * instance doesn't hold the level or a chunk in memory. A chunk unloaded while attached is still read from, as a read
 * racing the unload through the level would have been.
 */
public final class ChunkCachingView implements BlockGetter {

    private @Nullable Level level;
    private @Nullable LevelChunk chunk;
    private int chunkX;
    private int chunkZ;

    /**
     * Reads from the level until {@link #detach()}
     */
    public void attach(Level level) {
        this.level = level;
        this.chunk = null;
    }

    /**
     * Lets go of the level and the chunk in hand
     */
    public void detach() {
        this.level = null;
        this.chunk = null;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(@NotNull BlockPos pos) {
        return null;
    }

    @Override
    public @NotNull BlockState getBlockState(@NotNull BlockPos pos) {
        if (this.level().isOutsideBuildHeight(pos))
            return Blocks.VOID_AIR.defaultBlockState();
        return this.chunk(pos).getBlockState(pos);
    }

    @Override
    public @NotNull FluidState getFluidState(@NotNull BlockPos pos) {
        if (this.level().isOutsideBuildHeight(pos))
            return Fluids.EMPTY.defaultFluidState();
        return this.chunk(pos).getFluidState(pos);
    }

    @Override
    public int getHeight() {
        return this.level().getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return this.level().getMinBuildHeight();
    }

    private Level level() {
        return Objects.requireNonNull(this.level, "not attached to a level");
    }

    private LevelChunk chunk(BlockPos pos) {
        int x = SectionPos.blockToSectionCoord(pos.getX());
        int z = SectionPos.blockToSectionCoord(pos.getZ());
        var current = this.chunk;
        if (current == null || x != this.chunkX || z != this.chunkZ) {
            current = this.level().getChunk(x, z);
            this.chunk = current;
            this.chunkX = x;
            this.chunkZ = z;
        }
        return current;
    }
}

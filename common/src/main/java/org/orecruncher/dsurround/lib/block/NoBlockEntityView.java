package org.orecruncher.dsurround.lib.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * A level's blocks and fluids, without its block entities, for reading from a thread other than the client's.
 * <p>
 * On the client, {@code Level.getBlockEntity} has no guard for other threads (the server's returns null for them): if
 * the block should have a block entity that isn't there yet, it makes one and puts it in the chunk's map. Mod blocks
 * look theirs up from shape methods (Create's belt does, for its collision shape), so a ray trace from a worker could
 * write to that map while the client changes it or the renderer copies it, corrupting it (issue #166). Through this
 * view they get none, as from the server off its thread, and fall back to the shape they have without one.
 */
public record NoBlockEntityView(BlockGetter level) implements BlockGetter {

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.level.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.level.getFluidState(pos);
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return this.level.getMinBuildHeight();
    }
}

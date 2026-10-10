package org.orecruncher.dsurround.lib.block;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Matches every state of one block.
 */
class MatchOnBlock extends BlockStateMatcher {

    protected final Block block;

    MatchOnBlock(Block block) {
        this.block = block;
    }

    @Override
    public boolean isEmpty() {
        return this.block == Blocks.AIR || this.block == Blocks.CAVE_AIR || this.block == Blocks.VOID_AIR;
    }

    @Override
    public boolean match(BlockState state) {
        return state.getBlock() == this.block;
    }

    @Override
    public String toSpecification() {
        return BuiltInRegistries.BLOCK.getKey(this.block).toString();
    }

    @Override
    public int hashCode() {
        return this.block.hashCode();
    }

    /**
     * Equal to another matcher of exactly this class for the same block. A MatchOnBlockState for the same block is
     * not equal: it matches fewer states.
     */
    @Override
    public boolean equals(final Object obj) {
        return obj != null && obj.getClass() == this.getClass() && this.block == ((MatchOnBlock) obj).block;
    }
}

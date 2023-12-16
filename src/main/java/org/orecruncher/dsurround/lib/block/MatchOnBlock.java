package org.orecruncher.dsurround.lib.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

class MatchOnBlock extends BlockStateMatcher {

    private final Block block;
    private final Identifier blockId;

    MatchOnBlock(Block block) {
        this.block = block;
        this.blockId = Registries.BLOCK.getId(block);
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
    public int hashCode() {
        // Only do the block hash code.  Reason is that BlockStateMatcher does not honor the equality contract set
        // forth by Object.  Equals can perform a partial match.
        return this.block.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof final MatchOnBlock m) {
            return this.block == m.block;
        }
        return false;
    }

    @Override
    public String toString() {
        return this.blockId.toString();
    }
}

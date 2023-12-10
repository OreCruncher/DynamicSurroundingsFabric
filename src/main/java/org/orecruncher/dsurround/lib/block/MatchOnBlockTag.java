package org.orecruncher.dsurround.lib.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class MatchOnBlockTag extends BlockStateMatcher {

    private final TagKey<Block> tagId;

    MatchOnBlockTag(Identifier tagId) {
        this.tagId = TagKey.of(Registry.BLOCK_KEY, tagId);
    }

    @Override
    public boolean isEmpty() {
        return Registry.BLOCK.containsTag(tagId);
    }

    @Override
    public boolean match(BlockState state) {
        return state.isIn(this.tagId);
    }
}

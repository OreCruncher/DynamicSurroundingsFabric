package org.orecruncher.dsurround.lib.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKeys;

public class MatchOnBlockTag extends BlockStateMatcher {

    private final TagKey<Block> tagId;

    MatchOnBlockTag(Identifier tagId) {
        this.tagId = TagKey.of(RegistryKeys.BLOCK, tagId);
    }

    @Override
    public boolean isEmpty() {
        return false;
        //return Registry.BLOCK.containsTag(tagId);
    }

    @Override
    public boolean match(BlockState state) {
        return state.isIn(this.tagId);
    }
}

package org.orecruncher.dsurround.lib.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKeys;
import org.orecruncher.dsurround.tags.TagHelpers;

public class MatchOnBlockTag extends BlockStateMatcher {

    private final TagKey<Block> tagId;

    MatchOnBlockTag(Identifier tagId) {
        this.tagId = TagKey.of(RegistryKeys.BLOCK, tagId);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean match(BlockState state) {
        return TagHelpers.isIn(this.tagId, state.getRegistryEntry());
    }
}

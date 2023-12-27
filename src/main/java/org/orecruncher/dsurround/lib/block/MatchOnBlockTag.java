package org.orecruncher.dsurround.lib.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKeys;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;

public class MatchOnBlockTag extends BlockStateMatcher {

    private final static ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

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
        return TAG_LIBRARY.isIn(this.tagId, state.getBlock());
    }

    @Override
    public String toString() {
        return "MatchOnBlockTag{" + this.tagId.id().toString() + "}";
    }
}

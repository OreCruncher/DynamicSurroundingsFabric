package org.orecruncher.dsurround.lib.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;

public class MatchOnBlockTag extends BlockStateMatcher {

    private final static ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

    private final TagKey<Block> tagId;

    MatchOnBlockTag(ResourceLocation tagId) {
        this.tagId = TagKey.create(Registries.BLOCK, tagId);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean match(BlockState state) {
        return TAG_LIBRARY.is(this.tagId, state);
    }

    @Override
    public String toString() {
        return "MatchOnBlockTag{" + this.tagId.location() + "}";
    }
}

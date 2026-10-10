package org.orecruncher.dsurround.lib.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;

/**
 * Matches the states of every block in a block tag.
 */
final class MatchOnBlockTag extends BlockStateMatcher {

    // Resolved on first use rather than when the class loads, so tag matchers can be created (when configs are
    // parsed, or in tests) without the tag library being available yet
    private static final class Holder {
        static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);
    }

    private final TagKey<Block> tagId;

    MatchOnBlockTag(ResourceLocation tagId) {
        this.tagId = TagKey.create(Registries.BLOCK, tagId);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean isTagMatcher() {
        return true;
    }

    @Override
    public boolean match(BlockState state) {
        return Holder.TAG_LIBRARY.is(this.tagId, state);
    }

    @Override
    public String toSpecification() {
        return TAG_TYPE + this.tagId.location();
    }

    @Override
    public int hashCode() {
        return this.tagId.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof final MatchOnBlockTag other && this.tagId.equals(other.tagId);
    }
}

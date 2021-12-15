package org.orecruncher.dsurround.lib.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.tag.Tag;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.Optional;

public class MatchOnBlockTag extends BlockStateMatcher {

    private final Identifier tagId;

    MatchOnBlockTag(Identifier tagId) {
        this.tagId = tagId;
    }

    @Override
    public boolean isEmpty() {
        var tag = resolveTag();
        return tag.isEmpty() || tag.get().values().size() == 0;
    }

    @Override
    public boolean match(BlockState state) {
        var tag = resolveTag();
        return tag.isPresent() && tag.get().contains(state.getBlock());
    }

    private Optional<Tag<Block>> resolveTag() {
        try {
            var result = GameUtils.getWorld().getTagManager()
                    .getTag(Registry.BLOCK_KEY, tagId, id -> new RuntimeException("Tag not found in registry"));
            return Optional.ofNullable(result);
        } catch(Throwable ignored) {
        }
        return Optional.empty();
    }
}

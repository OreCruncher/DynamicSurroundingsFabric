package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.compat.BlockCompat;

import java.util.List;

/**
 * What counts as a roof overhead, for the ceiling scanner (whether the player is inside) and {@link Shelter} (how open
 * to the weather a place is), so the two agree about a building. A roof is solid enough to be one
 * ({@link BlockCompat#isCeilingSolid}: not a lantern or a ladder), spreads over at least {@link #MIN_FOOTPRINT} of its
 * block seen from below (not an upright chain, an end rod, iron bars or a pane, which that rule lets through for being
 * full height; nor an open trapdoor or a door), and isn't leaves, a fence, a fence gate or a wall: rain and wind get
 * through trees, and past a fence rail or the top of a wall overhead.
 */
public final class Roofs {

    /**
     * The share of its block a roof's shape covers, seen from below, at least
     */
    static final double MIN_FOOTPRINT = 0.5D;
    private static final List<TagKey<Block>> NOT_ROOFS = List.of(BlockTags.LEAVES, BlockTags.FENCE_GATES, BlockTags.FENCES, BlockTags.WALLS);

    private Roofs() {
    }

    /**
     * Whether a block is a roof over what is below it
     *
     * @param tags the tag library, which knows the mod's own tag definitions as well as the game's
     */
    public static boolean isRoof(BlockGetter level, BlockPos pos, BlockState state, ITagLibrary tags) {
        if (!BlockCompat.isCeilingSolid(level, pos, state))
            return false;
        var bounds = state.getCollisionShape(level, pos).bounds();
        if (bounds.getXsize() * bounds.getZsize() < MIN_FOOTPRINT)
            return false;
        for (var tag : NOT_ROOFS)
            if (tags.is(tag, state))
                return false;
        return true;
    }
}

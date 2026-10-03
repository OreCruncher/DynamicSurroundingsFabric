package org.orecruncher.dsurround.lib.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

public class BlockCompat {

    // Vanilla's thresholds for its legacy solid flag: a collision box whose average side is at least this long, or
    // which is full height, counts as solid
    private static final double LEGACY_SOLID_MIN_AVERAGE_SIZE = 0.7291666666666666;
    private static final double LEGACY_SOLID_MIN_HEIGHT = 1.0;

    /**
     * True if the block has a collision shape: something an entity can stand on or bump into. Air, plants, torches
     * and the like have none.
     * <p>
     * Replaces BlockState.isSolid() (and blocksMotion(), which builds on it), deprecated in 1.21.1. That is a flag
     * precomputed once per state against an empty world, requiring the collision shape to be large or full height.
     * This asks the block for its actual shape at {@code pos}, so blocks whose shape depends on position or context
     * get the right answer, and small blocks with collision (carpets, lanterns, chains, candles) count too. See
     * {@link #isCeilingSolid} for the stricter, size-based rule.
     * <p>
     * Cheap: for nearly all blocks the shape is a prebuilt constant, so there is no allocation, only a few calls.
     *
     * @param level where the block is
     * @param pos   the block's position
     * @param state the block's state at {@code pos}
     */
    public static boolean isSolid(final BlockGetter level, final BlockPos pos, final BlockState state) {
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * True if the block is solid enough to count as a roof: its collision shape is large (average side at least
     * about 0.73 of a block) or full height. Small blocks with collision, such as lanterns, chains, candles and end
     * rods, don't count.
     * <p>
     * This is the size rule behind the deprecated BlockState.isSolid() and blocksMotion(), applied to the block's
     * actual collision shape at {@code pos}. Vanilla also forces the flag on or off for some blocks, through
     * properties that can't be read here:
     * <ul>
     *   <li>ladders are forced off; without that a ladder passes (its box is full height), so ladders are excluded
     *       here too</li>
     *   <li>signs, hanging signs and cobwebs are forced on, but they have no collision, so they don't count here.
     *       blocksMotion() excluded cobwebs as well; signs are the one difference from it.</li>
     *   <li>moving pistons are forced on; here they get their actual shape</li>
     * </ul>
     * Computing the shape's bounds allocates a small box, so this costs a little more than {@link #isSolid}.
     *
     * @param level where the block is
     * @param pos   the block's position
     * @param state the block's state at {@code pos}
     */
    public static boolean isCeilingSolid(final BlockGetter level, final BlockPos pos, final BlockState state) {
        var shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty())
            return false;
        if (state.getBlock() instanceof LadderBlock)
            return false;
        var bounds = shape.bounds();
        return bounds.getSize() >= LEGACY_SOLID_MIN_AVERAGE_SIZE || bounds.getYsize() >= LEGACY_SOLID_MIN_HEIGHT;
    }
}

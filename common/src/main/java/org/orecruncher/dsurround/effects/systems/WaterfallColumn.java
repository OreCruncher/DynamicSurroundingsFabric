package org.orecruncher.dsurround.effects.systems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.orecruncher.dsurround.effects.BlockEffectUtils;

/**
 * The shapes falling fluid makes: the column above where a waterfall lands, and steps where flowing fluid drops one
 * block. Kept apart from the effect systems so it can be used (and tested) without the services they need.
 */
final class WaterfallColumn {

    private WaterfallColumn() {
    }

    /**
     * Whether the fluid is falling. Every vanilla flowing fluid has the FALLING property; anything without it can't be
     * falling.
     */
    static boolean isFalling(FluidState fluid) {
        return fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING);
    }

    /**
     * Whether falling fluid at {@code pos} has something to land on: a fluid source block (a pool) or a block with a
     * solid top below it. Otherwise it is still falling.
     */
    static boolean landsOn(BlockGetter world, BlockPos pos) {
        var below = pos.below();
        var state = world.getBlockState(below);
        return state.getFluidState().isSource() || state.isFaceSturdy(world, below, Direction.UP, SupportType.FULL);
    }

    /**
     * Whether a waterfall lands at {@code pos}: falling fluid directly above, the fluid here can spread sideways (see
     * {@link #canSpread}), and below is a source block or a solid top face to land on (see {@link #landsOn}). Says
     * nothing about which fluid it is; the effect system checks that with a tag.
     */
    static boolean isLandingSite(BlockGetter world, BlockPos pos) {
        // Falling fluid above, not just any fluid: otherwise the bottom of a still pool next to flowing water
        // qualifies. The same test that measures the waterfall's strength.
        return isFalling(world.getFluidState(pos.above())) && canSpread(world, pos) && landsOn(world, pos);
    }

    /**
     * Whether fluid at {@code pos} can spread sideways: one of the four sides is air, or holds fluid that only partly
     * fills the block. Fluid hemmed in on every side (a full pool, or walls) can't, and makes no waterfall.
     */
    static boolean canSpread(BlockGetter world, BlockPos pos) {
        var side = new BlockPos.MutableBlockPos();
        for (var direction : Direction.Plane.HORIZONTAL) {
            var state = world.getBlockState(side.setWithOffset(pos, direction));
            if (state.isAir())
                return true;
            // An empty fluid has an amount of 0
            int amount = state.getFluidState().getAmount();
            if (amount > 0 && amount < FluidState.AMOUNT_FULL)
                return true;
        }
        return false;
    }

    /**
     * Whether fluid at {@code pos} is a step: where flowing fluid drops a single block. It is falling, the fluid above
     * it (that it spilled from) isn't, and it lands on something. A drop of two or more blocks is a waterfall instead
     * (see {@link WaterfallEffectSystem}), which needs falling fluid above where it lands; so the two never overlap.
     * A run of steps down a slope is a step at each one.
     */
    static boolean isStep(BlockGetter world, BlockPos pos) {
        if (!isFalling(world.getFluidState(pos)))
            return false;
        var above = world.getFluidState(pos.above());
        return !above.isEmpty() && !isFalling(above) && landsOn(world, pos);
    }

    /**
     * A waterfall's strength: the height of its drop, as the number of blocks of falling fluid directly above the
     * landing block at {@code pos}, capped at {@link BlockEffectUtils#MAX_STRENGTH}. At least 1 for a waterfall,
     * which needs falling fluid above it; 0 if there is none.
     * <p>
     * Neither the landing block nor the fluid at the lip the water pours over (which isn't falling) counts. Counting
     * all fluid did, which made every waterfall 2 stronger than its drop, and 3 the weakest possible.
     */
    static int strength(BlockGetter world, BlockPos pos) {
        var mutable = pos.mutable();
        int count = 0;
        while (count < BlockEffectUtils.MAX_STRENGTH && isFalling(world.getFluidState(mutable.move(0, 1, 0))))
            count++;
        return count;
    }
}

package org.orecruncher.dsurround.effects.blocks.producers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.effects.blocks.BubbleJetEffect;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.Optional;

import static org.orecruncher.dsurround.effects.BlockEffectUtils.IS_WATER;

public class UnderwaterBubbleProducer extends BlockEffectProducer {

    public UnderwaterBubbleProducer(Script chance, Script conditions) {
        super(chance, conditions);
    }

    @Override
    protected boolean canTrigger(final Level world, final BlockState state,
                                 final BlockPos pos, final IRandomizer random) {
        if (IS_WATER.test(state)) {
            var belowPos = pos.below();
            var belowBlock = world.getBlockState(belowPos);
            var isSolidBlock = belowBlock.isFaceSturdy(world, belowPos, Direction.UP, SupportType.FULL);
            return isSolidBlock && super.canTrigger(world, state, pos, random);
        }
        return false;
    }

    @Override
    protected Optional<IBlockEffect> produceImpl(final Level world, final BlockState state,
                                              final BlockPos pos, final IRandomizer random) {
        var liquidBlocks = countVerticalBlocks(world, pos, IS_WATER, 1);
        if (liquidBlocks > 0) {
            var effect = new BubbleJetEffect(liquidBlocks, world, pos.getX() + 0.5D,
                    pos.getY() + 0.1D, pos.getZ() + 0.5D);
            return Optional.of(effect);
        }

        return Optional.empty();
    }
}
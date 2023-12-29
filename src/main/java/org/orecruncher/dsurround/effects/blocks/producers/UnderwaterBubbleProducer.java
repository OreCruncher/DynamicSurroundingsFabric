package org.orecruncher.dsurround.effects.blocks.producers;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.orecruncher.dsurround.effects.blocks.BubbleJetEffect;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.Optional;
import java.util.Random;

import static org.orecruncher.dsurround.effects.BlockEffectUtils.IS_WATER;

public class UnderwaterBubbleProducer extends BlockEffectProducer {

    public UnderwaterBubbleProducer(Script chance, Script conditions) {
        super(chance, conditions);
    }

    @Override
    protected boolean canTrigger(final World world, final BlockState state,
                                 final BlockPos pos, final Random random) {
        if (IS_WATER.test(state)) {
            var belowPos = pos.down();
            var belowBlock = world.getBlockState(belowPos);
            var isSolidBlock = belowBlock.isSolidBlock(world, belowPos);
            return isSolidBlock && super.canTrigger(world, state, pos, random);
        }
        return false;
    }

    @Override
    protected Optional<IBlockEffect> produceImpl(final World world, final BlockState state,
                                              final BlockPos pos, final Random random) {
        var liquidBlocks = countVerticalBlocks(world, pos, IS_WATER, 1);
        if (liquidBlocks > 0) {
            var effect = new BubbleJetEffect(liquidBlocks, world, pos.getX() + 0.5D,
                    pos.getY() + 0.1D, pos.getZ() + 0.5D);
            return Optional.of(effect);
        }

        return Optional.empty();
    }
}
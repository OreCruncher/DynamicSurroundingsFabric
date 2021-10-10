package org.orecruncher.dsurround.effects.producers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.orecruncher.dsurround.effects.BubbleJetEffect;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.Optional;
import java.util.Random;

@Environment(EnvType.CLIENT)
public class UnderwaterBubbleProducer extends BlockEffectProducer {

    public UnderwaterBubbleProducer(Script chance, Script conditions) {
        super(chance, conditions);
    }

    @Override
    protected boolean canTrigger(final World world, final BlockState state,
                                 final BlockPos pos, final Random random) {
        if (WATER_PREDICATE.test(state)) {
            var belowBlock = world.getBlockState(pos.down());
            var material = belowBlock.getMaterial();
            var isSolidBlock = material.isSolid();
            return isSolidBlock && super.canTrigger(world, state, pos, random);
        }
        return false;
    }

    @Override
    protected Optional<IBlockEffect> produceImpl(final World world, final BlockState state,
                                              final BlockPos pos, final Random random) {
        var liquidBlocks = countVerticalBlocks(world, pos, WATER_PREDICATE, 1);
        if (liquidBlocks > 0) {
            var effect = new BubbleJetEffect(liquidBlocks, world, pos.getX() + 0.5D,
                    pos.getY() + 0.1D, pos.getZ() + 0.5D);
            return Optional.of(effect);
        }

        return Optional.empty();
    }
}
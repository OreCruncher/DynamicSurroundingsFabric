package org.orecruncher.dsurround.effects.blocks.producers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.orecruncher.dsurround.effects.blocks.FlameJetEffect;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.Optional;
import java.util.Random;

import static org.orecruncher.dsurround.effects.BlockEffectUtils.IS_LAVA;

public class FlameJetProducer extends BlockEffectProducer {

    public FlameJetProducer(final Script chance, final Script conditions) {
        super(chance, conditions);
    }

    @Override
    protected boolean canTrigger(final Level world, final BlockState state,
                                 final BlockPos pos, final Random random) {
        return world.getBlockState(pos.above()).isAir() && super.canTrigger(world, state, pos, random);
    }

    @Override
    public Optional<IBlockEffect> produceImpl(final Level world, final BlockState state,
                                              final BlockPos pos, final Random random) {
        final int blockCount;
        final float spawnHeight;
        final boolean isNonLiquidBlock;

        if (!state.getFluidState().isEmpty()) {
            blockCount = countVerticalBlocks(world, pos, IS_LAVA, -1);
            spawnHeight = pos.getY() + state.getFluidState().getOwnHeight() + 0.1F;
            isNonLiquidBlock = false;
        } else {
            final VoxelShape shape = state.getShape(world, pos);
            if (shape.isEmpty()) {
                return Optional.empty();
            }
            final double blockHeight = shape.bounds().maxY;
            spawnHeight = (float) (pos.getY() + blockHeight);
            isNonLiquidBlock = true;
            if (state.isFaceSturdy(world, pos, Direction.UP, SupportType.FULL)) {
                blockCount = 2;
            } else {
                blockCount = 1;
            }
        }

        if (blockCount > 0) {
            var effect = new FlameJetEffect(blockCount, world, pos.getX() + 0.5D, spawnHeight, pos.getZ() + 0.5D, isNonLiquidBlock);
            return Optional.of(effect);
        }

        return Optional.empty();
    }
}
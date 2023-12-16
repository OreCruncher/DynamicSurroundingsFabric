package org.orecruncher.dsurround.effects.blocks.producers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.orecruncher.dsurround.effects.blocks.FlameJetEffect;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.Optional;
import java.util.Random;

@Environment(EnvType.CLIENT)
public class FlameJetProducer extends BlockEffectProducer {

    public FlameJetProducer(final Script chance, final Script conditions) {
        super(chance, conditions);
    }

    @Override
    protected boolean canTrigger(final World world, final BlockState state,
                                 final BlockPos pos, final Random random) {
        return world.getBlockState(pos.up()).isAir() && super.canTrigger(world, state, pos, random);
    }

    @Override
    public Optional<IBlockEffect> produceImpl(final World world, final BlockState state,
                                              final BlockPos pos, final Random random) {
        final int blockCount;
        final float spawnHeight;
        final boolean isSolid;

        if (!state.getFluidState().isEmpty()) {
            blockCount = countVerticalBlocks(world, pos, LAVA_PREDICATE, -1);
            spawnHeight = pos.getY() + state.getFluidState().getHeight() + 0.1F;
            isSolid = false;
        } else {
            final VoxelShape shape = state.getOutlineShape(world, pos);
            if (shape.isEmpty()) {
                return Optional.empty();
            }
            final double blockHeight = shape.getBoundingBox().maxY;
            spawnHeight = (float) (pos.getY() + blockHeight);
            isSolid = true;
            if (state.isSolid()) {
                blockCount = 2;
            } else {
                blockCount = 1;
            }
        }

        if (blockCount > 0) {
            var effect = new FlameJetEffect(blockCount, world, pos.getX() + 0.5D, spawnHeight, pos.getZ() + 0.5D, isSolid);
            return Optional.of(effect);
        }

        return Optional.empty();
    }
}
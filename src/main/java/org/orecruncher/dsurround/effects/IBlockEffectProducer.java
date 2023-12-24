package org.orecruncher.dsurround.effects;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.Random;

@FunctionalInterface
public interface IBlockEffectProducer {
    Optional<IBlockEffect> produce(World world, BlockState state, BlockPos pos, Random rand);
}

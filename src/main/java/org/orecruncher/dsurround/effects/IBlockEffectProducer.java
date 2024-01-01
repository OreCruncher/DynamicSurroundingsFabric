package org.orecruncher.dsurround.effects;


import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.Random;

@FunctionalInterface
public interface IBlockEffectProducer {
    Optional<IBlockEffect> produce(Level world, BlockState state, BlockPos pos, Random rand);
}

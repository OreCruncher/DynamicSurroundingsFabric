package org.orecruncher.dsurround.effects;


import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.lib.random.IRandomizer;

import java.util.Optional;

@FunctionalInterface
public interface IBlockEffectProducer {
    Optional<IBlockEffect> produce(Level world, BlockState state, BlockPos pos, IRandomizer rand);
}

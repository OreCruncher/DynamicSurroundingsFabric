package org.orecruncher.dsurround.effects;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.orecruncher.dsurround.effects.IBlockEffect;

import java.util.Optional;
import java.util.Random;

@Environment(EnvType.CLIENT)
@FunctionalInterface
public interface IBlockEffectProducer {
    Optional<IBlockEffect> produce(World world, BlockState state, BlockPos pos, Random rand);
}

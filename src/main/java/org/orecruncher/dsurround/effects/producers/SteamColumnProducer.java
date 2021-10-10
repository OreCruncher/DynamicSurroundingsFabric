package org.orecruncher.dsurround.effects.producers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.SteamJetEffect;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.Optional;
import java.util.Random;

@Environment(EnvType.CLIENT)
public class SteamColumnProducer extends BlockEffectProducer {

    public SteamColumnProducer(final Script chance, final Script conditions) {
        super(chance, conditions);
    }

    public static boolean isValidSpawnBlock(final World world, final BlockPos pos, final BlockState source) {
        var whatsUp = world.getBlockState(pos.up());
        if (!whatsUp.isAir())
            return false;
        if (world.getBlockState(pos) != source)
            return false;
        return countCubeBlocks(world, pos, HOTBLOCK_PREDICATE, true) > 0;
    }

    @Override
    protected boolean canTrigger(World world, BlockState state, BlockPos pos, Random rand) {
        return isValidSpawnBlock(world, pos, state) && super.canTrigger(world, state, pos, rand);
    }

    @Override
    protected Optional<IBlockEffect> produceImpl(final World world, final BlockState state,
                                              final BlockPos pos, final Random random) {
        var strength = countCubeBlocks(world, pos, HOTBLOCK_PREDICATE, false);
        if (strength > 0) {
            var fluidState = state.getFluidState();
            final float spawnHeight;
            if (fluidState.isEmpty()) {
                spawnHeight = pos.getY() + 0.9F;
            } else {
                spawnHeight = pos.getY() + fluidState.getHeight() + 0.1F;
            }

            var effect = new SteamJetEffect(strength, world, pos.getX() + 0.5D, spawnHeight, pos.getZ() + 0.5D);
            return Optional.of(effect);
        }

        return Optional.empty();
    }
}

package org.orecruncher.dsurround.effects.blocks.producers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.SideShapeType;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.blocks.WaterSplashJetEffect;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.Optional;
import java.util.Random;

@Environment(EnvType.CLIENT)
public class WaterSplashProducer extends BlockEffectProducer {

    private final static Vec3i[] cardinal_offsets = {
            new Vec3i(-1, 0, 0),
            new Vec3i(1, 0, 0),
            new Vec3i(0, 0, -1),
            new Vec3i(0, 0, 1)
    };

    public WaterSplashProducer(final Script chance, final Script conditions) {
        super(chance, conditions);
    }

    private static boolean isUnboundedLiquid(final World provider, final BlockPos pos) {
        for (final Vec3i cardinal_offset : cardinal_offsets) {
            final BlockPos tp = pos.add(cardinal_offset);
            final BlockState state = provider.getBlockState(tp);
            if (state.isAir())
                return true;
            final FluidState fluidState = state.getFluidState();
            final int height = fluidState.getLevel();
            if (height > 0 && height < 8)
                return true;
        }

        return false;
    }

    /**
     * Similar to isUnboundedLiquid() but geared towards determine that the liquid is bound on all sides.
     */
    private static boolean isBoundedLiquid(final World provider, final BlockPos pos) {
        for (final Vec3i cardinal_offset : cardinal_offsets) {
            final BlockPos tp = pos.add(cardinal_offset);
            final BlockState state = provider.getBlockState(tp);
            if (state.isAir())
                return false;
            final FluidState fluidState = state.getFluidState();
            if (fluidState.isEmpty()) {
                continue;
            }
            if (fluidState.get(FlowableFluid.FALLING))
                return false;
            final int height = fluidState.getLevel();
            if (height > 0 && height < 8)
                return false;
        }

        return true;
    }

    private int liquidBlockCount(final World provider, final BlockPos pos) {
        return countVerticalBlocks(provider, pos, FLUID_PREDICATE, 1);
    }

    public static boolean isValidSpawnBlock(final World provider, final BlockPos pos) {
        return isValidSpawnBlock(provider, provider.getBlockState(pos), pos);
    }

    private static boolean isValidSpawnBlock(final World provider, final BlockState state,
                                             final BlockPos pos) {
        if (state.getFluidState().isEmpty())
            return false;
        if (provider.getFluidState(pos.up()).isEmpty())
            return false;
        if (isUnboundedLiquid(provider, pos)) {
            BlockPos down = pos.down();
            var downState = provider.getBlockState(down);
            var solid = downState.isSideSolid(provider, down, Direction.UP, SideShapeType.FULL);
            if (solid)
                return true;
            return isBoundedLiquid(provider, down);
        }
        return false;
    }

    @Override
    protected boolean canTrigger(final World provider, final BlockState state,
                                 final BlockPos pos, final Random random) {
        return super.canTrigger(provider, state, pos, random) && isValidSpawnBlock(provider, state, pos);
    }

    @Override
    protected Optional<IBlockEffect> produceImpl(final World world, final BlockState state,
                                                 final BlockPos pos, final Random random) {

        final int strength = liquidBlockCount(world, pos);
        if (strength > 1) {
            final float height = state.getFluidState().getHeight(world, pos) + 0.1F;
            var effect = new WaterSplashJetEffect(strength, world, pos, height);
            return Optional.of(effect);
        }

        return Optional.empty();
    }
}
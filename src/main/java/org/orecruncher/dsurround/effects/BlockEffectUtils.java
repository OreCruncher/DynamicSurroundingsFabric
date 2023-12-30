package org.orecruncher.dsurround.effects;

import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.tags.BlockEffectTags;

import java.util.function.Predicate;

public class BlockEffectUtils {

    private BlockEffectUtils() {

    }

    private static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

    public static final int MAX_STRENGTH = 10;

    public static final Predicate<BlockState> HAS_FLUID = (state) -> !state.getFluidState().isEmpty();

    public static final Predicate<BlockState> IS_LAVA = (state) -> state.getFluidState().isIn(FluidTags.LAVA);

    public static final Predicate<BlockState> IS_WATER = (state) -> state.getFluidState().isIn(FluidTags.WATER);

    // Covers blast furnace
    public static final Predicate<BlockState> IS_LIT_FURNACE = (state) ->
            state.getBlock() instanceof AbstractFurnaceBlock && state.get(AbstractFurnaceBlock.LIT);

    public static final Predicate<BlockState> IS_LIT_CAMPFIRE = CampfireBlock::isLitCampfire;

    public static final Predicate<BlockState> IS_HEAT_PRODUCER = (state) ->
            TAG_LIBRARY.isIn(BlockEffectTags.HEAT_PRODUCERS, state.getBlock());

    public static final Predicate<BlockState> IS_HOT_SOURCE = (state) ->
            IS_HEAT_PRODUCER.test(state) || IS_LIT_FURNACE.test(state) || IS_LIT_CAMPFIRE.test(state);

    public static boolean blockExistsAround(
        final World provider,
        final BlockPos pos,
        final Predicate<BlockState> predicate) {

        for (int k = -1; k <= 1; k++)
            for (int j = -1; j <= 1; j++)
                for (int i = -1; i <= 1; i++) {
                    final BlockState state = provider.getBlockState(pos.add(i, j, k));
                    if (predicate.test(state)) {
                        return true;
                    }
                }
        return false;
    }

    public static int countVerticalBlocks(final World provider,
                                          final BlockPos pos,
                                          final Predicate<BlockState> predicate,
                                          final int step) {
        int count = 0;
        final BlockPos.Mutable mutable = pos.mutableCopy();
        for (; count < MAX_STRENGTH && predicate.test(provider.getBlockState(mutable)); count++)
            mutable.setY(mutable.getY() + step);
        return MathHelper.clamp(count, 0, MAX_STRENGTH);
    }
}

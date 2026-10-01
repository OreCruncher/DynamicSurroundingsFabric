package org.orecruncher.dsurround.effects;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
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

    public static final Predicate<BlockState> IS_LAVA = (state) -> state.getFluidState().is(FluidTags.LAVA);

    public static final Predicate<BlockState> IS_WATER = (state) -> state.getFluidState().is(FluidTags.WATER);

    // Covers blast furnace
    public static final Predicate<BlockState> IS_LIT_FURNACE = (state) ->
            state.getBlock() instanceof AbstractFurnaceBlock && state.getValue(AbstractFurnaceBlock.LIT);

    public static final Predicate<BlockState> IS_LIT_CAMPFIRE = CampfireBlock::isLitCampfire;

    public static final Predicate<BlockState> IS_HEAT_PRODUCER = (state) ->
            TAG_LIBRARY.is(BlockEffectTags.HEAT_PRODUCERS, state);

    public static final Predicate<BlockState> IS_HOT_SOURCE = (state) ->
            IS_HEAT_PRODUCER.test(state) || IS_LIT_FURNACE.test(state) || IS_LIT_CAMPFIRE.test(state);

    /**
     * True if any block in the 3x3x3 cube centered on {@code pos} (including {@code pos}) matches the predicate.
     * A plain loop with one reusable position: this is called for many scanned blocks, so it avoids the iterator
     * and lambda that BlockPos.findClosestMatch allocates per call.
     */
    public static boolean blockExistsAround(
        final Level provider,
        final BlockPos pos,
        final Predicate<BlockState> predicate) {
        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 1; dy++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++) {
                    if (predicate.test(provider.getBlockState(mutable.setWithOffset(pos, dx, dy, dz))))
                        return true;
                }
        return false;
    }

    public static int countVerticalBlocks(final Level provider,
                                          final BlockPos pos,
                                          final Predicate<BlockState> predicate,
                                          final int step) {
        int count = 0;
        final BlockPos.MutableBlockPos mutable = pos.mutable();
        for (; count < MAX_STRENGTH && predicate.test(provider.getBlockState(mutable)); count++)
            mutable.setY(mutable.getY() + step);
        return Mth.clamp(count, 0, MAX_STRENGTH);
    }
}

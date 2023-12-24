package org.orecruncher.dsurround.effects.blocks.producers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

@Environment(EnvType.CLIENT)
public abstract class BlockEffectProducer implements IBlockEffectProducer {

    protected final IConditionEvaluator conditionEvaluator;
    protected final Script chance;
    protected final Script conditions;


    protected BlockEffectProducer(Script chance, Script conditions) {
        this.chance = chance;
        this.conditions = conditions;
        this.conditionEvaluator = ContainerManager.resolve(IConditionEvaluator.class);
    }

    protected boolean canTrigger(World world, BlockState state, BlockPos pos, Random rand) {
        if (this.conditionEvaluator.check(this.conditions)) {
            var chance = this.conditionEvaluator.eval(this.chance);
            return chance instanceof Double c && rand.nextDouble() < c;
        }
        return false;
    }

    @Override
    public Optional<IBlockEffect> produce(World world, BlockState state, BlockPos pos, Random rand) {
        if (this.canTrigger(world, state, pos, rand)) {
            return this.produceImpl(world, state, pos, rand);
        }
        return Optional.empty();
    }

    protected abstract Optional<IBlockEffect> produceImpl(World world, BlockState state, BlockPos pos, Random rand);

    //
    // Bunch of helper methods for implementations
    //
    public static final int MAX_STRENGTH = 10;

    public static final Predicate<BlockState> FLUID_PREDICATE = (state) -> !state.getFluidState().isEmpty();

    public static final Predicate<BlockState> LAVA_PREDICATE = (state) -> state.getFluidState().isIn(FluidTags.LAVA);

    public static final Predicate<BlockState> WATER_PREDICATE = (state) -> state.getFluidState().isIn(FluidTags.WATER);

    // Covers blast furnace
    public static final Predicate<BlockState> LIT_FURNACE = (state) ->
            state.getBlock() instanceof AbstractFurnaceBlock && state.get(AbstractFurnaceBlock.LIT);

    public static final Predicate<BlockState> LIT_CAMPFIRE = CampfireBlock::isLitCampfire;

    public static final Predicate<BlockState> MAGMA_BLOCK = (state) ->
            state.getBlock() == Blocks.MAGMA_BLOCK;

    public static final Predicate<BlockState> HOTBLOCK_PREDICATE = (state) ->
            LAVA_PREDICATE.test(state) || MAGMA_BLOCK.test(state) || LIT_FURNACE.test(state) || LIT_CAMPFIRE.test(state);

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

    public static int countCubeBlocks(final World provider,
                                      final BlockPos pos,
                                      final Predicate<BlockState> predicate,
                                      final boolean fastFirst) {
        int blockCount = 0;
        for (int k = -1; k <= 1; k++)
            for (int j = -1; j <= 1; j++)
                for (int i = -1; i <= 1; i++) {
                    final BlockState state = provider.getBlockState(pos.add(i, j, k));
                    if (predicate.test(state)) {
                        if (fastFirst)
                            return 1;
                        blockCount++;
                    }
                }
        return blockCount;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName()
                + "{chance: " + this.chance
                + "; conditions: " + this.conditions + "}";
    }
}

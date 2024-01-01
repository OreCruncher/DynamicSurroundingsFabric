package org.orecruncher.dsurround.effects.blocks.producers;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

public abstract class BlockEffectProducer implements IBlockEffectProducer {

    protected final IConditionEvaluator conditionEvaluator;
    protected final Script chance;
    protected final Script conditions;


    protected BlockEffectProducer(Script chance, Script conditions) {
        this.chance = chance;
        this.conditions = conditions;
        this.conditionEvaluator = ContainerManager.resolve(IConditionEvaluator.class);
    }

    protected boolean canTrigger(Level world, BlockState state, BlockPos pos, Random rand) {
        if (this.conditionEvaluator.check(this.conditions)) {
            var chance = this.conditionEvaluator.eval(this.chance);
            return chance instanceof Double c && rand.nextDouble() < c;
        }
        return false;
    }

    @Override
    public Optional<IBlockEffect> produce(Level world, BlockState state, BlockPos pos, Random rand) {
        if (this.canTrigger(world, state, pos, rand)) {
            return this.produceImpl(world, state, pos, rand);
        }
        return Optional.empty();
    }

    protected abstract Optional<IBlockEffect> produceImpl(Level world, BlockState state, BlockPos pos, Random rand);

    //
    // Bunch of helper methods for implementations
    //
    public static final int MAX_STRENGTH = 10;

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

    public static int countCubeBlocks(final Level provider,
                                      final BlockPos pos,
                                      final Predicate<BlockState> predicate,
                                      final boolean fastFirst) {
        int blockCount = 0;
        for (int k = -1; k <= 1; k++)
            for (int j = -1; j <= 1; j++)
                for (int i = -1; i <= 1; i++) {
                    final BlockState state = provider.getBlockState(pos.offset(i, j, k));
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

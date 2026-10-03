package org.orecruncher.dsurround.effects.blocks.producers;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

import java.util.Optional;

public abstract class BlockEffectProducer implements IBlockEffectProducer {

    protected final IConditionEvaluator conditionEvaluator;
    protected final Script chance;
    protected final Script conditions;


    protected BlockEffectProducer(Script chance, Script conditions) {
        this(ContainerManager.resolve(IConditionEvaluator.class), chance, conditions);
    }

    protected BlockEffectProducer(IConditionEvaluator conditionEvaluator, Script chance, Script conditions) {
        this.chance = chance;
        this.conditions = conditions;
        this.conditionEvaluator = conditionEvaluator;
    }

    protected boolean canTrigger(Level world, BlockState state, BlockPos pos, IRandomizer rand) {
        if (this.conditionEvaluator.check(this.conditions))
            return rollChance(this.conditionEvaluator.eval(this.chance), rand);
        return false;
    }

    /**
     * Whether a roll succeeds for {@code chance}, a script result. Any number counts (a script function can return
     * an integer or a float, not just the double a literal gives); anything else never succeeds.
     */
    static boolean rollChance(Object chance, IRandomizer rand) {
        return chance instanceof Number c && rand.nextDouble() < c.doubleValue();
    }

    @Override
    public Optional<IBlockEffect> produce(Level world, BlockState state, BlockPos pos, IRandomizer rand) {
        if (this.canTrigger(world, state, pos, rand)) {
            return this.produceImpl(world, state, pos, rand);
        }
        return Optional.empty();
    }

    protected abstract Optional<IBlockEffect> produceImpl(Level world, BlockState state, BlockPos pos, IRandomizer rand);

    @Override
    public String toString() {
        return this.getClass().getSimpleName()
                + "{chance: " + this.chance
                + "; conditions: " + this.conditions + "}";
    }
}

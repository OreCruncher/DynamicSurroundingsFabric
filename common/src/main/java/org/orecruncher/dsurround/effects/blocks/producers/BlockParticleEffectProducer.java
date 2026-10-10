package org.orecruncher.dsurround.effects.blocks.producers;

import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.Optional;

/**
 * Special block effect producer that just produces particle effects - no fancy systems.  As a result
 * no block effect will be generated and placed into the tracking system.
 */
public class BlockParticleEffectProducer extends BlockEffectProducer {

    private final IParticleSupplier supplier;

    public BlockParticleEffectProducer(Script chance, Script conditions, IParticleSupplier particleSupplier) {
        super(chance, conditions);
        this.supplier = particleSupplier;
    }

    public BlockParticleEffectProducer(IConditionEvaluator conditionEvaluator, Script chance, Script conditions, IParticleSupplier particleSupplier) {
        super(conditionEvaluator, chance, conditions);
        this.supplier = particleSupplier;
    }

    @Override
    final protected Optional<IBlockEffect> produceImpl(Level world, BlockState state, BlockPos pos, IRandomizer rand) {
        var particle = this.supplier.create(world, state, pos, rand);
        // A supplier's vanilla fallback can come back empty; the particle engine doesn't take null
        if (particle != null)
            this.addParticle(particle);
        return Optional.empty();
    }

    protected void addParticle(final Particle particle) {
        GameUtils.getParticleManager().add(particle);
    }

    @FunctionalInterface
    public interface IParticleSupplier {
        @Nullable Particle create(Level world, BlockState state, BlockPos pos, IRandomizer rand);
    }
}

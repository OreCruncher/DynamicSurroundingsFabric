package org.orecruncher.dsurround.effects.blocks.producers;

import net.minecraft.block.BlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.Optional;
import java.util.Random;

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

    @Override
    final protected Optional<IBlockEffect> produceImpl(World world, BlockState state, BlockPos pos, Random rand) {
        var particle = this.supplier.create(world, state, pos, rand);
        this.addParticle(particle);
        return Optional.empty();
    }

    protected void addParticle(final Particle particle) {
        GameUtils.getParticleManager().addParticle(particle);
    }

    @FunctionalInterface
    public interface IParticleSupplier {
        Particle create(World world, BlockState state, BlockPos pos, Random rand);
    }
}

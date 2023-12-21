package org.orecruncher.dsurround.effects.blocks.producers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.particle.ParticleEffect;
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
@Environment(EnvType.CLIENT)
public abstract class BlockParticleEffectProducer extends BlockEffectProducer {

    public BlockParticleEffectProducer(Script chance, Script conditions) {
        super(chance, conditions);
    }

    @Override
    final protected Optional<IBlockEffect> produceImpl(World world, BlockState state, BlockPos pos, Random rand) {
        this.produceParticle(world, state, pos, rand);
        return Optional.empty();
    }

    protected abstract void produceParticle(World world, BlockState state, BlockPos pos, Random rand);

    protected void addParticle(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        GameUtils.getParticleManager().addParticle(parameters, x, y, z, velocityX, velocityY, velocityZ);
    }

    protected void addParticle(final Particle particle) {
        GameUtils.getParticleManager().addParticle(particle);
    }
}

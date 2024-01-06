package org.orecruncher.dsurround.effects.blocks;

import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;

import java.util.Optional;

public class BubbleJetEffect extends AbstractParticleEmitterEffect {

    public BubbleJetEffect(final int strength, final Level world, final double x, final double y, final double z) {
        super(strength, world, x, y, z);
    }

    @Override
    protected Optional<Particle> produceParticle() {
        return this.createParticle(ParticleTypes.BUBBLE, this.posX, this.posY, this.posZ, 0, 0.5D + this.strength / 10D, 0D);
    }
}
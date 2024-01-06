package org.orecruncher.dsurround.effects.blocks;

import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;

import java.util.Optional;

public class FlameJetEffect extends AbstractParticleEmitterEffect {

    private static final ISoundFactory FIRE_AMBIENT = SoundFactoryBuilder
            .create(SoundEvents.FIRE_AMBIENT)
            .build();

    protected final boolean isLava;
    protected final SimpleParticleType particleType;
    protected final boolean isNonLiquidBlock;
    protected boolean soundFired;

    public FlameJetEffect(final int strength, final Level world, final double x, final double y, final double z, boolean isNonLiquidBlock) {
        super(strength, world, x, y, z);
        this.isLava = !isNonLiquidBlock && RANDOM.nextInt(3) == 0;
        this.particleType = this.isLava ? ParticleTypes.LAVA : ParticleTypes.FLAME;
        this.isNonLiquidBlock = isNonLiquidBlock;
    }

    @Override
    protected void soundUpdate() {
        if (!this.soundFired) {
            this.soundFired = true;
            if (this.strength > 1) {
                var soundInstance = FIRE_AMBIENT.createAtLocation(getPos());
                AUDIO_PLAYER.play(soundInstance);
            }
        }
    }

    @Override
    protected Optional<Particle> produceParticle() {
        double speedY = this.isLava ? 0 : this.strength / 10.0D;
        float scale = this.strength;
        double x = this.posX;
        double z = this.posZ;

        if (this.isNonLiquidBlock) {
            x += (RANDOM.nextDouble() - RANDOM.nextDouble()) * 0.5D;
            z += (RANDOM.nextDouble() - RANDOM.nextDouble()) * 0.5D;
            if (this.strength == 1) {
                speedY *= 0.5D;
                scale *= 0.5F;
            }
        }

        var particle = this.createParticle(this.particleType, x, this.posY, z, 0, speedY, 0D);
        float finalScale = scale;
        particle.ifPresent(p -> {
            if (p instanceof FlameParticle) {
                p.scale(finalScale);
            }
        });

        return particle;
    }
}
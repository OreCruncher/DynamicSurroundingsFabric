package org.orecruncher.dsurround.effects.blocks;

import net.minecraft.client.particle.FlameParticle;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;

public class FlameJetEffect extends ParticleJetEffect {

    private static final ISoundFactory FIRE_AMBIENT = SoundFactoryBuilder
            .create(SoundEvents.BLOCK_FIRE_AMBIENT)
            .build();

    protected final boolean isLava;
    protected final DefaultParticleType particleType;
    protected final IAudioPlayer audioPlayer;
    protected final boolean isSolid;
    protected boolean soundFired;

    public FlameJetEffect(final int strength, final World world, final double x, final double y, final double z, boolean isSolid) {
        super(strength, world, x, y, z);
        this.isLava = !isSolid && RANDOM.nextInt(3) == 0;
        this.particleType = this.isLava ? ParticleTypes.LAVA : ParticleTypes.FLAME;
        this.isSolid = isSolid;
        this.audioPlayer = ContainerManager.resolve(IAudioPlayer.class);
    }

    @Override
    protected void soundUpdate() {
        if (!this.soundFired) {
            this.soundFired = true;
            if (this.jetStrength > 1) {
                var soundInstance = FIRE_AMBIENT.createAtLocation(getPos());
                this.audioPlayer.play(soundInstance);
            }
        }
    }

    @Override
    protected void spawnJetParticle() {
        double speedY = this.isLava ? 0 : this.jetStrength / 10.0D;
        float scale = this.jetStrength;
        double x = this.posX;
        double z = this.posZ;

        if (this.isSolid) {
            x += (RANDOM.nextDouble() - RANDOM.nextDouble()) * 0.5D;
            z += (RANDOM.nextDouble() - RANDOM.nextDouble()) * 0.5D;
            if (this.jetStrength == 1) {
                speedY *= 0.5D;
                scale *= 0.5F;
            }
        }

        var particle = this.createParticle(this.particleType, x, this.posY, z, 0, speedY, 0D);

        if (particle instanceof FlameParticle) {
            particle.scale(scale);
        }

        this.addParticle(particle);
    }
}
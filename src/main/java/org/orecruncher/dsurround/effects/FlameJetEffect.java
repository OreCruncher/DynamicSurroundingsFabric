package org.orecruncher.dsurround.effects;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import org.orecruncher.dsurround.sound.MinecraftAudioPlayer;
import org.orecruncher.dsurround.sound.SoundFactory;

@Environment(EnvType.CLIENT)
public class FlameJetEffect extends ParticleJetEffect {

    protected final boolean isLava;
    protected final DefaultParticleType particleType;
    protected final boolean isSolid;
    protected boolean soundFired;

    public FlameJetEffect(final int strength, final World world, final double x, final double y, final double z, boolean isSolid) {
        super(strength, world, x, y, z);
        this.isLava = !isSolid && RANDOM.nextInt(3) == 0;
        this.particleType = this.isLava ? ParticleTypes.LAVA : ParticleTypes.FLAME;
        this.isSolid = isSolid;
    }

    @Override
    protected void soundUpdate() {
        if (!this.soundFired) {
            this.soundFired = true;
            if (this.jetStrength > 1) {
                var soundInstance = SoundFactory.createAtLocation(SoundEvents.BLOCK_FIRE_AMBIENT, getPos());
                MinecraftAudioPlayer.INSTANCE.play(soundInstance);
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

        final Particle particle = this.addParticle(this.particleType, x, this.posY, z, 0, speedY, 0D);

        if (particle instanceof FlameParticle) {
            particle.scale(scale);
        }
    }
}
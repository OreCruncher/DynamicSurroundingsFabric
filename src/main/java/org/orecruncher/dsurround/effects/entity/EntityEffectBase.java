package org.orecruncher.dsurround.effects.entity;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.particle.ParticleEffect;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.mixins.core.MixinParticleManager;
import org.orecruncher.dsurround.sound.IAudioPlayer;

public abstract class EntityEffectBase implements IEntityEffect {

    protected final IAudioPlayer audioPlayer;

    public EntityEffectBase() {
        this.audioPlayer = ContainerManager.resolve(IAudioPlayer.class);
    }

    /**
     * Override to perform logic for initializing the effect beyond the ctor.
     */
    public void activate(EntityEffectInfo manager) {

    }

    /**
     * Called before the effect is removed from the various maps so that cleanup
     * can be performed.
     */
    public void deactivate(EntityEffectInfo manager) {

    }

    /**
     * Override to provide logic for the effect
     */
    public void tick(EntityEffectInfo manager) {

    }

    /**
     * Helper method to play a sound.
     */
    public void playSound(SoundInstance sound) {
        this.audioPlayer.play(sound);
    }

    /**
     * Helper method to add a particle to the particle system
     */
    public void addParticle(Particle particle) {
        GameUtils.getParticleManager().addParticle(particle);
    }

    /**
     * Creates a particle effect but does not queue.  Allows for the particle to be manipulated prior to handing
     * it to the particle manager.
     */
    public <T extends ParticleEffect> Particle createParticle(T parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        return ((MixinParticleManager) GameUtils.getParticleManager())
                .dsurroundCreateParticle(
                        parameters,
                        x, y, z,
                        velocityX, velocityY, velocityZ);
    }
}

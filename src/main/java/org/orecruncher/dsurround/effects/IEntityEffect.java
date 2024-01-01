package org.orecruncher.dsurround.effects;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.sound.IAudioPlayer;

public interface IEntityEffect {

    /**
     * Override to perform logic for initializing the effect beyond the ctor.
     */
    default void activate(EntityEffectInfo manager) {

    }

    /**
     * Called before the effect is removed from the various maps so that cleanup
     * can be performed.
     */
    default void deactivate(EntityEffectInfo manager) {

    }

    /**
     * Override to provide logic for the effect
     */
    default void tick(EntityEffectInfo manager) {

    }

    /**
     * Helper method to play a sound.
     */
    default void playSound(SoundInstance sound) {
        ContainerManager.resolve(IAudioPlayer.class).play(sound);
    }

    /**
     * Helper method to add a particle to the particle system
     */
    default void addParticle(Particle particle) {
        GameUtils.getParticleManager().add(particle);
    }

}

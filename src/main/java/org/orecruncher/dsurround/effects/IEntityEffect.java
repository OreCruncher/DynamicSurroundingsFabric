package org.orecruncher.dsurround.effects;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.sound.SoundInstance;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.sound.MinecraftAudioPlayer;

@Environment(EnvType.CLIENT)
public interface IEntityEffect {

    /**
     * Override to perform logic for initializing the effect beyond the ctor.
     */
    default void initialize(EntityEffectInfo manager) {

    }

    /**
     * Called before the effect is removed from the various maps so that cleanup
     * can be performed.
     */
    default void deinitialize(EntityEffectInfo manager) {

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
        MinecraftAudioPlayer.INSTANCE.play(sound);
    }

    /**
     * Helper method to add a particle to the particle system
     */
    default void addParticle(Particle particle) {
        GameUtils.getMC().particleManager.addParticle(particle);
    }

}

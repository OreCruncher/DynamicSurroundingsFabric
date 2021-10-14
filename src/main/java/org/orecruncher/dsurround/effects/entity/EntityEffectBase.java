package org.orecruncher.dsurround.effects.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.sound.SoundInstance;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.sound.MinecraftAudioPlayer;

@Environment(EnvType.CLIENT)
public abstract class EntityEffectBase implements IEntityEffect {

    public EntityEffectBase() {

    }

    /**
     * Override to perform logic for initializing the effect beyond the ctor.
     */
    public void initialize(EntityEffectInfo manager) {

    }

    /**
     * Called before the effect is removed from the various maps so that cleanup
     * can be performed.
     */
    public void deinitialize(EntityEffectInfo manager) {

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
        MinecraftAudioPlayer.INSTANCE.play(sound);
    }

    /**
     * Helper method to add a particle to the particle system
     */
    public void addParticle(Particle particle) {
        GameUtils.getMC().particleManager.addParticle(particle);
    }

}

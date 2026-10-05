package org.orecruncher.dsurround.effects.entity;

import net.minecraft.client.resources.sounds.SoundInstance;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.sound.IAudioPlayer;

public abstract class EntityEffectBase implements IEntityEffect {

    // Effects get their libraries through their constructors. The audio player is looked up the first time a sound
    // plays rather than when the class loads, so effects can be loaded (and tested) without a container.
    private static final class AudioPlayerHolder {
        static final IAudioPlayer AUDIO_PLAYER = ContainerManager.resolve(IAudioPlayer.class);
    }

    public EntityEffectBase() {
    }

    /**
     * Helper method to play a sound.
     */
    public void playSound(SoundInstance sound) {
        AudioPlayerHolder.AUDIO_PLAYER.play(sound);
    }

    @Override
    public String toString() {
        return "EFFECT: " + this.getClass().getSimpleName();
    }
}

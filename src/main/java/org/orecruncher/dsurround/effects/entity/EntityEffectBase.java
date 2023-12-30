package org.orecruncher.dsurround.effects.entity;

import net.minecraft.client.sound.SoundInstance;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.sound.IAudioPlayer;

public abstract class EntityEffectBase implements IEntityEffect {

    protected final IAudioPlayer audioPlayer;

    public EntityEffectBase() {
        this.audioPlayer = ContainerManager.resolve(IAudioPlayer.class);
    }

    /**
     * Helper method to play a sound.
     */
    public void playSound(SoundInstance sound) {
        this.audioPlayer.play(sound);
    }

}

package org.orecruncher.dsurround.effects.entity;

import net.minecraft.client.resources.sounds.SoundInstance;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.sound.IAudioPlayer;

public abstract class EntityEffectBase implements IEntityEffect {

    protected static final ISoundLibrary SOUND_LIBRARY = ContainerManager.resolve(ISoundLibrary.class);

    protected static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);
    private static final IAudioPlayer AUDIO_PLAYER = ContainerManager.resolve(IAudioPlayer.class);

    public EntityEffectBase() {
    }

    /**
     * Helper method to play a sound.
     */
    public void playSound(SoundInstance sound) {
        AUDIO_PLAYER.play(sound);
    }

    @Override
    public String toString() {
        return "EFFECT: " + this.getClass().getSimpleName();
    }
}

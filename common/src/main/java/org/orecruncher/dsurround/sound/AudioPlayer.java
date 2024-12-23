package org.orecruncher.dsurround.sound;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;

public class AudioPlayer implements IAudioPlayer {

    private final SoundManager manager;

    public AudioPlayer(SoundManager manager) {
        this.manager = manager;
    }

    @Override
    public void play(SoundInstance sound) {
        this.manager.play(sound);
    }

    @Override
    public void stop(SoundInstance sound) {
        this.manager.stop(sound);
    }

    @Override
    public void stopAll() {
        this.manager.stop();
    }

    @Override
    public boolean isPlaying(SoundInstance sound) {
        return this.manager.isActive(sound);
    }
}

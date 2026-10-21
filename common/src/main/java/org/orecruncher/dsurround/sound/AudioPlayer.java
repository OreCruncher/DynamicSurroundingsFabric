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
        if (this.isSoundSystemAvailable())
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

    @Override
    public boolean isSoundSystemAvailable() {
        // Not asking OpenAL whether the device is still connected: the sound engine asks every tick and reloads when
        // it is lost, and the query waits whenever another thread is opening a device (voice chat's microphone, say)
        return this.manager.soundEngine.loaded;
    }
}

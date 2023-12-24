package org.orecruncher.dsurround.sound;

import net.minecraft.client.sound.SoundInstance;

public interface IAudioPlayer {

    void play(SoundInstance sound);

    void stop(SoundInstance sound);

    void stopAll();

    boolean isPlaying(SoundInstance sound);
}

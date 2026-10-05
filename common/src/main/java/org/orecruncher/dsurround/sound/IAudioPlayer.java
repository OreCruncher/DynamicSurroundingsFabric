package org.orecruncher.dsurround.sound;

import net.minecraft.client.resources.sounds.SoundInstance;

/**
 * Plays sounds through Minecraft's sound manager. The debug implementation also logs each play and stop.
 */
public interface IAudioPlayer {

    void play(SoundInstance sound);

    void stop(SoundInstance sound);

    void stopAll();

    boolean isPlaying(SoundInstance sound);

    /**
     * Whether the sound engine is loaded and its audio device is still connected. Plays are skipped otherwise.
     */
    boolean isSoundSystemAvailable();
}

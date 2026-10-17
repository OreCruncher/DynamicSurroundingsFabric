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
     * Whether the sound engine is loaded. Plays are skipped otherwise. A lost audio device is noticed by the engine's
     * own tick, which reloads it.
     */
    boolean isSoundSystemAvailable();
}

package org.orecruncher.dsurround.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;

@Environment(EnvType.CLIENT)
public interface IAudioPlayer {

    void play(SoundInstance sound);

    void stop(SoundInstance sound);

    void stopAll();

    boolean isPlaying(SoundInstance sound);
}

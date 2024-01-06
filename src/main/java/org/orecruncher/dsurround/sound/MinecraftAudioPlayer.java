package org.orecruncher.dsurround.sound;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;

public class MinecraftAudioPlayer implements IAudioPlayer {

    private final IModLog logger;
    private final SoundManager manager;

    public MinecraftAudioPlayer(SoundManager manager, IModLog logger) {
        this.logger = logger;
        this.manager = manager;
    }

    @Override
    public void play(SoundInstance sound) {
        this.logger.debug(Configuration.Flags.AUDIO_PLAYER, () -> String.format("PLAYING %s", formatSound(sound)));
        this.manager.play(sound);
    }

    @Override
    public void stop(SoundInstance sound) {
        this.logger.debug(Configuration.Flags.AUDIO_PLAYER, () -> String.format("STOPPING %s", formatSound(sound)));
        this.manager.stop(sound);
    }

    @Override
    public void stopAll() {
        this.logger.debug(Configuration.Flags.AUDIO_PLAYER, "STOPPING all sounds");
        this.manager.stop();
    }

    @Override
    public boolean isPlaying(SoundInstance sound) {
        return this.manager.isActive(sound);
    }

    protected String formatSound(SoundInstance sound) {
        return AudioUtilities.debugString(sound);
    }

}

package org.orecruncher.dsurround.sound;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;

public class AudioPlayerDebug extends AudioPlayer {

    private final IModLog logger;

    public AudioPlayerDebug(SoundManager manager, IModLog logger) {
        super(manager);
        this.logger = ModLog.createChild(logger, "AudioPlayer");
    }

    @Override
    public void play(SoundInstance sound) {
        this.logger.debug(Configuration.Flags.AUDIO_PLAYER, () -> String.format("PLAYING %s", formatSound(sound)));
        super.play(sound);
    }

    @Override
    public void stop(SoundInstance sound) {
        this.logger.debug(Configuration.Flags.AUDIO_PLAYER, () -> String.format("STOPPING %s", formatSound(sound)));
        super.stop(sound);
    }

    @Override
    public void stopAll() {
        this.logger.debug(Configuration.Flags.AUDIO_PLAYER, "STOPPING all sounds");
        super.stopAll();
    }

    protected String formatSound(SoundInstance sound) {
        return AudioUtilities.debugString(sound);
    }
}

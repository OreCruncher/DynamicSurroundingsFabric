package org.orecruncher.dsurround.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;

@Environment(EnvType.CLIENT)
public class MinecraftAudioPlayer implements IAudioPlayer {

    private static final IModLog LOGGER = Client.LOGGER.createChild(MinecraftAudioPlayer.class);

    public static IAudioPlayer INSTANCE = new MinecraftAudioPlayer(GameUtils.getSoundHander());

    private final SoundManager manager;

    public MinecraftAudioPlayer(SoundManager manager) {
        this.manager = manager;
    }

    @Override
    public void play(SoundInstance sound) {
        LOGGER.debug(Configuration.Flags.AUDIO_PLAYER, () -> String.format("PLAYING %s", formatSound(sound)));
        this.manager.play(sound);
    }

    @Override
    public void stop(SoundInstance sound) {
        LOGGER.debug(Configuration.Flags.AUDIO_PLAYER, () -> String.format("STOPPING %s", formatSound(sound)));
        this.manager.stop(sound);
    }

    @Override
    public void stopAll() {
        LOGGER.debug(Configuration.Flags.AUDIO_PLAYER, "STOPPING all sounds");
        this.manager.stopAll();
    }

    @Override
    public boolean isPlaying(SoundInstance sound) {
        return this.manager.isPlaying(sound);
    }

    protected String formatSound(SoundInstance sound) {
        return AudioUtilities.debugString(sound);
    }

}

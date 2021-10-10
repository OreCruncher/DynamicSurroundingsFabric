package org.orecruncher.dsurround.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.mixins.MixinAbstractSoundInstance;

@Environment(EnvType.CLIENT)
public class MinecraftAudioPlayer implements IAudioPlayer {

    private static final IModLog LOGGER = Client.LOGGER.createChild(MinecraftAudioPlayer.class);

    public static IAudioPlayer INSTANCE = new MinecraftAudioPlayer(GameUtils.getSoundHander());

    private final SoundManager manager;
    private final StringBuilder TOSTRING_BUILDER;

    public MinecraftAudioPlayer(SoundManager manager) {
        this.manager = manager;
        this.TOSTRING_BUILDER = new StringBuilder(128);
    }

    @Override
    public void play(SoundInstance sound) {
        LOGGER.debug(Configuration.Flags.AUDIO_PLAYER, "PLAYING %s", formatSound(sound));
        this.manager.play(sound);
    }

    @Override
    public void stop(SoundInstance sound) {
        LOGGER.debug(Configuration.Flags.AUDIO_PLAYER, "STOPPING %s", formatSound(sound));
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
        MixinAbstractSoundInstance accessor = (MixinAbstractSoundInstance) sound;
        this.TOSTRING_BUILDER.setLength(0);
        this.TOSTRING_BUILDER.append(sound.getClass().getSimpleName()).append("{[");
        this.TOSTRING_BUILDER.append(sound.getId()).append("]");
        this.TOSTRING_BUILDER.append(", ").append(sound.getCategory().getName());
        this.TOSTRING_BUILDER.append(String.format(", v: %.4f, p: %.4f", accessor.getRawVolume(), accessor.getRawVolume()));
        this.TOSTRING_BUILDER.append(String.format(", l: (%.2f,%.2f,%.2f)", sound.getX(), sound.getY(), sound.getZ()));
        this.TOSTRING_BUILDER.append(", a: ").append(sound.getAttenuationType().toString());
        this.TOSTRING_BUILDER.append(", g: ").append(sound.isRelative());
        this.TOSTRING_BUILDER.append("}");
        return this.TOSTRING_BUILDER.toString();
    }

}

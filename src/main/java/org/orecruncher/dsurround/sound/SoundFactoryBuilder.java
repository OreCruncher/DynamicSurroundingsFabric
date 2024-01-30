package org.orecruncher.dsurround.sound;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.util.valueproviders.UniformFloat;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;

@SuppressWarnings("unused")
public final class SoundFactoryBuilder {

    final SoundEvent soundEvent;
    FloatProvider volume;
    FloatProvider pitch;
    SoundSource category;
    boolean isRepeatable = false;
    int repeatDelay = 0;
    boolean global = false;
    SoundInstance.Attenuation attenuation;

    int musicMinDelay = SoundFactory.MusicSettings.DEFAULT.minDelay();
    int musicMaxDelay = SoundFactory.MusicSettings.DEFAULT.maxDelay();
    boolean musicReplaceMusic = SoundFactory.MusicSettings.DEFAULT.replaceCurrentMusic();

    SoundFactoryBuilder(SoundEvent soundEvent) {
        this.soundEvent = soundEvent;
        this.volume = ConstantFloat.of(1F);
        this.pitch = ConstantFloat.of(1F);
        this.category = SoundSource.AMBIENT;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
    }

    public SoundFactoryBuilder volume(float vol) {
        return this.volume(ConstantFloat.of(vol));
    }

    public SoundFactoryBuilder volume(float min, float max) {
        return this.volume(Float.compare(min, max) == 0 ? ConstantFloat.of(min) : UniformFloat.of(min, max));
    }

    public SoundFactoryBuilder volume(FloatProvider provider) {
        this.volume = provider;
        return this;
    }

    public SoundFactoryBuilder pitch(float pitch) {
        return this.pitch(ConstantFloat.of(pitch));
    }

    public SoundFactoryBuilder pitch(float min, float max) {
        return this.pitch(Float.compare(min, max) == 0 ? ConstantFloat.of(min) : UniformFloat.of(min, max));
    }

    public SoundFactoryBuilder pitch(FloatProvider provider) {
        this.pitch = provider;
        return this;
    }

    public SoundFactoryBuilder category(SoundSource category) {
        this.category = category;
        return this;
    }

    public SoundFactoryBuilder repeatable() {
        this.isRepeatable = true;
        this.repeatDelay = 0;
        return this;
    }

    public SoundFactoryBuilder repeatable(int delay) {
        this.isRepeatable = true;
        this.repeatDelay = delay;
        return this;
    }

    public SoundFactoryBuilder attenuation(SoundInstance.Attenuation attenuation) {
        this.attenuation = attenuation;
        this.global = attenuation == SoundInstance.Attenuation.NONE;
        return this;
    }

    public SoundFactoryBuilder global() {
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.global = true;
        return this;
    }

    public SoundFactoryBuilder setMusicMinDelay(int delay) {
        this.musicMinDelay = delay;
        return this;
    }

    public SoundFactoryBuilder setMusicMaxDelay(int delay) {
        this.musicMaxDelay = delay;
        return this;
    }

    public SoundFactoryBuilder setMusicReplaceCurrentMusic(boolean flag) {
        this.musicReplaceMusic = flag;
        return this;
    }

    public ISoundFactory build() {
        return SoundFactory.from(this);
    }

    public static SoundFactoryBuilder create(String soundEventId) {
        var se = ContainerManager.resolve(ISoundLibrary.class).getSound(soundEventId);
        return create(se);
    }

    public static SoundFactoryBuilder create(ResourceLocation soundEventId) {
        var se = ContainerManager.resolve(ISoundLibrary.class).getSound(soundEventId);
        return create(se);
    }

    public static SoundFactoryBuilder create(SoundEvent soundEvent) {
        return new SoundFactoryBuilder(soundEvent);
    }

    public static SoundFactoryBuilder create(Music music) {
        return new SoundFactoryBuilder(music.getEvent().value())
                .setMusicMinDelay(music.getMinDelay())
                .setMusicMaxDelay(music.getMaxDelay())
                .setMusicReplaceCurrentMusic(music.replaceCurrentMusic());
    }

}

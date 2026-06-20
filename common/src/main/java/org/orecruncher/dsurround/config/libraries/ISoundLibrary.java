package org.orecruncher.dsurround.config.libraries;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.orecruncher.dsurround.config.IndividualSoundConfigEntry;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundMetadata;

import java.util.Collection;
import java.util.Optional;

public interface ISoundLibrary extends ILibrary {

    SoundEvent getSound(final String sound);
    SoundEvent getSound(final Identifier sound);
    Collection<SoundEvent> getRegisteredSoundEvents();
    SoundMetadata getSoundMetadata(final Identifier sound);
    Optional<ISoundFactory> getSoundFactory(Identifier factoryLocation);
    ISoundFactory getSoundFactoryOrDefault(Identifier factoryLocation);
    ISoundFactory getSoundFactoryForMusic(Music music);

    Optional<SoundInstance> remapSound(SoundInstance soundInstance);
    boolean isBlocked(final Identifier id);
    boolean isCulled(final Identifier id);
    float getVolumeScale(SoundSource category, Identifier id);
    Optional<SoundEvent> getRandomStartupSound();
    Collection<IndividualSoundConfigEntry> getIndividualSoundConfigs();
    void saveIndividualSoundConfigs(Collection<IndividualSoundConfigEntry> configs);
}

package org.orecruncher.dsurround.config.libraries;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.orecruncher.dsurround.config.IndividualSoundConfigEntry;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundMetadata;

import java.util.Collection;
import java.util.Optional;

public interface ISoundLibrary extends ILibrary {

    SoundEvent getSound(final String sound);
    SoundEvent getSound(final ResourceLocation sound);
    Collection<SoundEvent> getRegisteredSoundEvents();
    SoundMetadata getSoundMetadata(final ResourceLocation sound);
    Optional<ISoundFactory> getSoundFactory(ResourceLocation factoryLocation);
    ISoundFactory getSoundFactoryOrDefault(ResourceLocation factoryLocation);

    boolean isBlocked(final ResourceLocation id);
    boolean isCulled(final ResourceLocation id);
    float getVolumeScale(SoundSource category, ResourceLocation id);
    Optional<SoundEvent> getRandomStartupSound();
    Collection<IndividualSoundConfigEntry> getIndividualSoundConfigs();
    void saveIndividualSoundConfigs(Collection<IndividualSoundConfigEntry> configs);
}

package org.orecruncher.dsurround.config.libraries;

import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.sound.SoundMetadata;

import java.util.Collection;

public interface ISoundLibrary extends ILibrary {

    SoundEvent getSound(final String sound);
    SoundEvent getSound(final Identifier sound);
    Collection<SoundEvent> getRegisteredSoundEvents();
    SoundMetadata getSoundMetadata(final Identifier sound);
    Identifier resolveIdentifier(final String defaultDomain, final String name);
}

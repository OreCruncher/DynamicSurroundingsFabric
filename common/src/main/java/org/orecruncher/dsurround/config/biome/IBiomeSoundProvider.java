package org.orecruncher.dsurround.config.biome;

import net.minecraft.sounds.Music;
import org.orecruncher.dsurround.config.AcousticEntryCollection;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Collection;
import java.util.Optional;

public interface IBiomeSoundProvider {

    /**
     * Gets a collection of SoundEvents that match the existing conditions within the game.
     *
     * @return Collection of matching SoundEvents.
     */
    Collection<ISoundFactory> findBiomeSoundMatches();

    /**
     * Gets an add-on SoundEvent based on existing conditions within the game as well
     * as configuration.
     *
     * @param type   Type of SoundEvent to retrieve
     * @param random Randomizer to use
     * @return SoundEvent that matches the criteria, if any
     */
    Optional<ISoundFactory> getExtraSound(SoundEventType type, IRandomizer random);

    /**
     * The music that can be chosen for the biome: the configured music plus the game's track. The same collection is
     * returned while the game offers the same track, so a different collection means the choices have changed.
     *
     * @param vanilla The music the game would play for the biome
     */
    AcousticEntryCollection getMusicChoices(Optional<Music> vanilla);
}

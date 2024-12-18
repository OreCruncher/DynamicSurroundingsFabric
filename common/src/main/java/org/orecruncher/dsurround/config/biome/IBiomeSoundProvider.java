package org.orecruncher.dsurround.config.biome;

import net.minecraft.sounds.Music;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Collection;
import java.util.Optional;

public interface IBiomeSoundProvider {

    /**
     * Gets a collection of SoundEvents that match the existing conditions within game.
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
     * @return SoundEvent that matches crtieria, if any
     */
    Optional<ISoundFactory> getExtraSound(SoundEventType type, IRandomizer random);

    /**
     * Creates a Music instance to be used with Minecraft's music manager
     */
    Optional<Music> getBackgroundMusic(IRandomizer randomizer);
}

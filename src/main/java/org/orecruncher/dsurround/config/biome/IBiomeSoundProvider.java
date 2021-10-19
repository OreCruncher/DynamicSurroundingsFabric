package org.orecruncher.dsurround.config.biome;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Collection;
import java.util.Random;

@Environment(EnvType.CLIENT)
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
    @Nullable ISoundFactory getExtraSound(SoundEventType type, Random random);

}

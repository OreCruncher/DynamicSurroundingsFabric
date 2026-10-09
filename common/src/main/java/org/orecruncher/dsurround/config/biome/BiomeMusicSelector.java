package org.orecruncher.dsurround.config.biome;

import net.minecraft.sounds.Music;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.AcousticEntryCollection;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Optional;

/**
 * Chooses the background music for the biome the player is in. The music manager asks every tick, but the choice
 * only matters when a track starts, so it is kept until asked to choose again or the choices change: a different
 * biome, a different track offered by the game, or a configuration reload.
 */
public final class BiomeMusicSelector {

    @Nullable
    private AcousticEntryCollection chosenFrom;
    @Nullable
    private Music chosen;

    /**
     * @param biome       The biome the music is for
     * @param vanilla     The music the game would play for the biome, folded into the choices
     * @param random      Randomizer to use
     * @param chooseAgain Make a new choice even if one is kept
     */
    public Optional<Music> select(IBiomeSoundProvider biome, Optional<Music> vanilla, IRandomizer random, boolean chooseAgain) {
        var choices = biome.getMusicChoices(vanilla);
        if (chooseAgain || this.chosen == null || this.chosenFrom != choices) {
            this.chosen = choices.makeSelection(random).map(ISoundFactory::createAsMusic).orElse(null);
            this.chosenFrom = choices;
        }
        return Optional.ofNullable(this.chosen);
    }
}

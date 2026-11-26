// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.biome;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.world.attribute.BackgroundMusic;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.config.data.AcousticConfig;
import org.orecruncher.dsurround.config.data.BiomeConfigRule;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.weighted.WeightValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The game's own background music for a biome, made into a rule so that it is chosen alongside the configured music
 * and shows with it in the biome dump and diagnostics. The library applies it after the configured rules, so a rule
 * clearing sounds (most clear the ambient loops before adding their own) doesn't take the game's music away.
 * <ul>
 *     <li>The biome's own track, if it sets one, is always a choice. A biome without music of its own plays its
 *     dimension's track only when the configured music gives nothing (the mixin falls back to it).</li>
 *     <li>The creative track is a choice while {@code playCreativeMusicWhileSurvival} is on and the player isn't in
 *     creative: the biome's, or else its dimension's (a forest sets only a default track, so it has none of its own).
 *     A biome without music of its own then offers its dimension's track too, so the creative music shares the
 *     table with it rather than replacing it.</li>
 * </ul>
 * A player in creative already gets the creative music from the game, so it isn't added for them.
 */
public final class GameMusicRule {

    /**
     * When the creative music may be chosen: the option on and the player not in creative
     */
    public static final Script CREATIVE_CONDITIONS = new Script("global.playCreativeMusic() && !player.isCreative()");
    public static final String COMMENT = "Game music";
    private static final WeightValue WEIGHT = WeightValue.of(10);

    private GameMusicRule() {
    }

    /**
     * @param biome     the music the biome sets for itself, or null if it sets none (it plays its dimension's)
     * @param dimension the music of the biome's dimension, or null if not known
     * @param sounds    where the tracks' factories are made, keeping each track's delays
     * @return the rule, or empty if there is no music to offer
     */
    public static Optional<BiomeConfigRule> of(@Nullable BackgroundMusic biome, @Nullable BackgroundMusic dimension, ISoundLibrary sounds) {
        var acoustics = new ArrayList<AcousticConfig>();

        var own = biome != null ? biome.defaultMusic() : Optional.<Music>empty();
        own.ifPresent(music -> add(acoustics, music, Script.TRUE, sounds));

        var creative = biome != null && biome.creativeMusic().isPresent()
                ? biome.creativeMusic()
                : dimension != null ? dimension.creativeMusic() : Optional.<Music>empty();
        if (creative.isPresent()) {
            if (own.isEmpty() && dimension != null)
                dimension.defaultMusic().ifPresent(music -> add(acoustics, music, CREATIVE_CONDITIONS, sounds));
            add(acoustics, creative.get(), CREATIVE_CONDITIONS, sounds);
        }

        if (acoustics.isEmpty())
            return Optional.empty();

        return Optional.of(new BiomeConfigRule(Script.TRUE, Optional.of(COMMENT), 0, false, List.of(), false, false,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), acoustics, List.of()));
    }

    // A track already offered isn't offered again under other conditions, which would double its weight
    private static void add(List<AcousticConfig> acoustics, Music music, Script conditions, ISoundLibrary sounds) {
        // Made here so the factory keeps the track's delays; the rule finds it by the sound's id, which it is kept under
        sounds.getSoundFactoryForMusic(music);
        Identifier factory = music.sound().value().location();
        for (var a : acoustics)
            if (a.factory().equals(factory))
                return;
        acoustics.add(new AcousticConfig(factory, conditions, WEIGHT, SoundEventType.MUSIC));
    }
}

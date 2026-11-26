// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.biome;

import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.Music;
import net.minecraft.world.attribute.BackgroundMusic;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.config.data.AcousticConfig;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.system.ISystemClock;
import org.orecruncher.dsurround.lib.threading.RecordingLog;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.testing.Evaluators;
import org.orecruncher.dsurround.testing.Fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.orecruncher.dsurround.config.biome.BiomeInfoMusicTests.*;

/**
 * The game's music for a biome as a rule: the biome's own track always, the creative track (the biome's or its
 * dimension's) only under the option's conditions, and the dimension's track beside it where the biome has none.
 */
public class GameMusicRuleTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final Script ALWAYS = Script.TRUE;
    private static final Script CREATIVE = GameMusicRule.CREATIVE_CONDITIONS;

    private static BackgroundMusic background(String defaultTrack, String creativeTrack) {
        return new BackgroundMusic(track(defaultTrack), track(creativeTrack), Optional.empty());
    }

    private static Optional<Music> track(String name) {
        return name == null ? Optional.empty() : Optional.of(music(id(name)));
    }

    // Each track's id with the conditions it is offered under
    private static Map<Identifier, Script> offered(BackgroundMusic biome, BackgroundMusic dimension) {
        return GameMusicRule.of(biome, dimension, BiomeInfoMusicTests.SOUNDS).orElseThrow().acoustics().stream()
                .collect(Collectors.toMap(AcousticConfig::factory, AcousticConfig::conditions));
    }

    @Test
    void noMusicGivesNoRule() {
        assertTrue(GameMusicRule.of(null, null, BiomeInfoMusicTests.SOUNDS).isEmpty());
        assertTrue(GameMusicRule.of(BackgroundMusic.EMPTY, BackgroundMusic.EMPTY, BiomeInfoMusicTests.SOUNDS).isEmpty());
    }

    @Test
    void aBiomeWithoutCreativeMusicAnywhereOffersItsOwnTrackAlways() {
        // The Nether's biomes: no creative music in the biome or the dimension
        assertEquals(Map.of(id("nether"), ALWAYS), offered(background("nether", null), null));
    }

    @Test
    void aBiomeWithItsOwnTrackTakesTheDimensionsCreativeMusic() {
        // A forest sets only a default track
        assertEquals(Map.of(id("forest"), ALWAYS, id("creative"), CREATIVE),
                offered(background("forest", null), background("game", "creative")));
    }

    @Test
    void aBiomesOwnCreativeMusicComesFirst() {
        // The rivers and oceans set their own
        assertEquals(Map.of(id("river"), ALWAYS, id("river_creative"), CREATIVE),
                offered(background("river", "river_creative"), background("game", "creative")));
    }

    @Test
    void aBiomeWithoutMusicOffersTheDimensionsTrackBesideTheCreativeMusic() {
        // Plains: the dimension's track only shares the table with the creative music, and otherwise plays only when
        // the configured music gives nothing, as before
        assertEquals(Map.of(id("game"), CREATIVE, id("creative"), CREATIVE),
                offered(null, background("game", "creative")));
    }

    @Test
    void aDimensionWithoutCreativeMusicAddsNothingToABiomeWithoutMusic() {
        assertTrue(GameMusicRule.of(null, background("end", null), BiomeInfoMusicTests.SOUNDS).isEmpty());
    }

    @Test
    void aTrackIsNotOfferedTwice() {
        assertEquals(Map.of(id("same"), ALWAYS), offered(background("same", "same"), null));
    }

    @Test
    void theRuleIsMusicWithADefaultWeight() {
        var rule = GameMusicRule.of(background("forest", "creative"), null, BiomeInfoMusicTests.SOUNDS).orElseThrow();
        assertEquals(Optional.of(GameMusicRule.COMMENT), rule.comment());
        assertFalse(rule.clearSounds());
        for (var acoustic : rule.acoustics()) {
            assertEquals(SoundEventType.MUSIC, acoustic.type());
            assertEquals(10, acoustic.weight().asInt());
        }
    }

    @Test
    void eachTracksFactoryIsMadeFromTheTrack() {
        // So the factory keeps the track's delays, rather than being made from its id alone
        List<Music> made = new ArrayList<>();
        ISoundLibrary sounds = Fakes.of(ISoundLibrary.class, Map.of(
                "getSoundFactoryForMusic", args -> {
                    made.add((Music) args[0]);
                    return Fakes.of(ISoundFactory.class, Map.of());
                }));
        GameMusicRule.of(background("forest", null), background("game", "creative"), sounds);
        assertEquals(List.of(id("forest"), id("creative")), made.stream().map(BiomeInfoMusicTests::location).toList());
    }

    @Test
    void theCreativeConditionsFollowTheOption() {
        // In the game's own engine: the player's state is never updated there, so the player isn't in creative
        for (var on : new boolean[]{false, true}) {
            var config = new Configuration();
            config.musicManagerOptions.playCreativeMusicWhileSurvival = on;
            var log = new RecordingLog();
            var evaluator = Evaluators.conditions(log, Fakes.of(ISystemClock.class, Map.of()), config);
            assertEquals(on, evaluator.eval(CREATIVE));
        }
    }
}

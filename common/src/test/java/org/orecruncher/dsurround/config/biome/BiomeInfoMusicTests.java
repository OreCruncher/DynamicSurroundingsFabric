// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.biome;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.attribute.BackgroundMusic;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.ConfigServices;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;
import org.orecruncher.dsurround.config.data.AcousticConfig;
import org.orecruncher.dsurround.config.data.BiomeConfigRule;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.threading.RecordingLog;
import org.orecruncher.dsurround.lib.weighted.WeightValue;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.testing.Fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The background music a biome offers: the configured music alongside the game's, which the library adds as a rule
 * of its own ({@link GameMusicRule}). Which of them plays is up to {@link BiomeMusicSelector}.
 */
public class BiomeInfoMusicTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("test", path);
    }

    static Music music(Identifier location) {
        return new Music(Holder.direct(SoundEvent.createVariableRangeEvent(location)), 100, 200, false);
    }

    static Identifier location(Music music) {
        return music.sound().value().location();
    }

    private static ISoundFactory soundFactory(Identifier location) {
        var asMusic = music(location);
        return Fakes.of(ISoundFactory.class, Map.of(
                "getLocation", args -> location,
                "createAsMusic", args -> asMusic));
    }

    static final ISoundLibrary SOUNDS = Fakes.of(ISoundLibrary.class, Map.of(
            "getSoundFactoryOrDefault", args -> soundFactory((Identifier) args[0]),
            "getSoundFactoryForMusic", args -> soundFactory(location((Music) args[0]))));

    private static final IConditionEvaluator CONDITIONS = Fakes.of(IConditionEvaluator.class, Map.of(
            "eval", args -> 0D,
            "check", args -> true));

    /**
     * Info for a biome with the rules applied in order
     */
    static BiomeInfo info(BiomeConfigRule... rules) {
        var services = new ConfigServices(new RecordingLog(), SOUNDS, Fakes.of(ITagLibrary.class, Map.of()), CONDITIONS);
        var builder = new BiomeInfoBuilder(1, id("biome"), "biome", BiomeTraits.of(), services);
        for (var rule : rules)
            builder.apply(rule);
        return builder.build();
    }

    static BiomeConfigRule musicRule(boolean clearSounds, String... sounds) {
        var acoustics = new ArrayList<AcousticConfig>();
        for (var s : sounds)
            acoustics.add(new AcousticConfig(id(s), Script.TRUE, WeightValue.of(10), SoundEventType.MUSIC));
        return new BiomeConfigRule(Script.TRUE, Optional.empty(), 0, false, List.of(), clearSounds, false,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), acoustics, List.of());
    }

    /**
     * The rule the library makes from the game's music for a biome that sets its own track
     */
    static BiomeConfigRule gameMusic(String track) {
        var own = new BackgroundMusic(Optional.of(music(id(track))), Optional.empty(), Optional.empty());
        return GameMusicRule.of(own, null, SOUNDS).orElseThrow();
    }

    private static Set<Identifier> choices(BiomeInfo info) {
        return info.getMusicChoices().stream()
                .map(e -> e.getAcoustic().getLocation())
                .collect(Collectors.toSet());
    }

    @Test
    void noMusicAnywhereGivesNoChoices() {
        assertTrue(info().getMusicChoices().isEmpty());
    }

    @Test
    void theGameTrackAloneIsTheChoice() {
        assertEquals(Set.of(id("vanilla")), choices(info(gameMusic("vanilla"))));
    }

    @Test
    void configuredMusicAloneWhenTheGameOffersNone() {
        var info = info(musicRule(false, "one", "two"));
        assertEquals(Set.of(id("one"), id("two")), choices(info));
    }

    @Test
    void configuredMusicIsOfferedAlongsideTheGameTrack() {
        var info = info(musicRule(false, "one"), gameMusic("vanilla"));
        assertEquals(Set.of(id("one"), id("vanilla")), choices(info));
    }

    @Test
    void clearingSoundsBeforeTheGameMusicKeepsIt() {
        // The library applies the game's music after the configured rules
        var info = info(musicRule(false, "one"), musicRule(true, "two"), gameMusic("vanilla"));
        assertEquals(Set.of(id("two"), id("vanilla")), choices(info));
    }

    @Test
    void theGameTrackIsListedWithTheConfiguredMusic() {
        // So the biome dump and diagnostics show it
        var info = info(musicRule(false, "one"), gameMusic("vanilla"));
        assertEquals(2, info.getSounds(SoundEventType.MUSIC).size());
        assertTrue(info.getComments().contains(GameMusicRule.COMMENT));
    }

    @Test
    void musicFromEachRuleIsOffered() {
        var info = info(musicRule(false, "one"), musicRule(false, "two"), gameMusic("vanilla"));
        assertEquals(Set.of(id("one"), id("two"), id("vanilla")), choices(info));
    }

    @Test
    void theSameChoicesAreReturnedUntilAReload() {
        // How the selector knows the choices haven't changed
        var info = info(musicRule(false, "one"), gameMusic("vanilla"));
        assertSame(info.getMusicChoices(), info.getMusicChoices());
    }

    @Test
    void theBuilderCantBeUsedOnceBuilt() {
        var services = new ConfigServices(new RecordingLog(), SOUNDS, Fakes.of(ITagLibrary.class, Map.of()), CONDITIONS);
        var builder = new BiomeInfoBuilder(1, id("biome"), "biome", BiomeTraits.of(), services);
        builder.build();
        assertThrows(IllegalStateException.class, () -> builder.apply(musicRule(false, "one")));
        assertThrows(IllegalStateException.class, builder::build);
    }
}

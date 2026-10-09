package org.orecruncher.dsurround.config.biome;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.ConfigServices;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;
import org.orecruncher.dsurround.config.data.AcousticConfig;
import org.orecruncher.dsurround.config.data.BiomeConfigRule;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.threading.RecordingLog;
import org.orecruncher.dsurround.lib.weighted.WeightValue;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.testing.Fakes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Background music for a biome: the configured music is chosen alongside the track the game offers for the biome.
 */
public class BiomeInfoMusicTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final int DRAWS = 200;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static Music music(ResourceLocation location) {
        return new Music(Holder.direct(SoundEvent.createVariableRangeEvent(location)), 100, 200, false);
    }

    private static ResourceLocation location(Music music) {
        return music.getEvent().value().getLocation();
    }

    private static ISoundFactory soundFactory(ResourceLocation location) {
        var asMusic = music(location);
        return Fakes.of(ISoundFactory.class, Map.of(
                "getLocation", args -> location,
                "createAsMusic", args -> asMusic));
    }

    private static final ISoundLibrary SOUNDS = Fakes.of(ISoundLibrary.class, Map.of(
            "getSoundFactoryOrDefault", args -> soundFactory((ResourceLocation) args[0]),
            "getSoundFactoryForMusic", args -> soundFactory(location((Music) args[0]))));

    private static final IConditionEvaluator CONDITIONS = Fakes.of(IConditionEvaluator.class, Map.of(
            "eval", args -> 0D,
            "check", args -> true));

    private static BiomeInfo info() {
        var services = new ConfigServices(new RecordingLog(), SOUNDS, Fakes.of(ITagLibrary.class, Map.of()), CONDITIONS);
        return new BiomeInfo(1, id("biome"), "biome", BiomeTraits.of(), services);
    }

    private static BiomeConfigRule musicRule(boolean clearSounds, String... sounds) {
        var acoustics = new ArrayList<AcousticConfig>();
        for (var s : sounds)
            acoustics.add(new AcousticConfig(id(s), Script.TRUE, WeightValue.of(10), SoundEventType.MUSIC));
        return new BiomeConfigRule(Script.TRUE, Optional.empty(), 0, false, List.of(), clearSounds, false,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), acoustics);
    }

    /**
     * The tracks chosen over many draws.
     */
    private static Set<ResourceLocation> chosen(BiomeInfo info, Optional<Music> vanilla) {
        IRandomizer random = Randomizer.create(42);
        var result = new HashSet<ResourceLocation>();
        for (int i = 0; i < DRAWS; i++)
            info.getBackgroundMusic(vanilla, random).ifPresent(m -> result.add(location(m)));
        return result;
    }

    @Test
    void noMusicAnywhereGivesNone() {
        assertTrue(info().getBackgroundMusic(Optional.empty(), Randomizer.create(42)).isEmpty());
    }

    @Test
    void withoutConfiguredMusicTheGameTrackPlays() {
        assertEquals(Set.of(id("vanilla")), chosen(info(), Optional.of(music(id("vanilla")))));
    }

    @Test
    void configuredMusicAloneWhenTheGameOffersNone() {
        var info = info();
        info.update(musicRule(false, "one", "two"));
        assertEquals(Set.of(id("one"), id("two")), chosen(info, Optional.empty()));
    }

    @Test
    void configuredMusicIsChosenAlongsideTheGameTrack() {
        var info = info();
        info.update(musicRule(false, "one"));
        assertEquals(Set.of(id("one"), id("vanilla")), chosen(info, Optional.of(music(id("vanilla")))));
    }

    @Test
    void clearingSoundsKeepsTheGameTrack() {
        var info = info();
        info.update(musicRule(false, "one"));
        info.update(musicRule(true, "two"));
        assertEquals(Set.of(id("two"), id("vanilla")), chosen(info, Optional.of(music(id("vanilla")))));
    }

    @Test
    void theGameTrackIsNotListedWithTheConfiguredMusic() {
        var info = info();
        info.update(musicRule(false, "one"));
        info.getBackgroundMusic(Optional.of(music(id("vanilla"))), Randomizer.create(42));
        assertEquals(1, info.getSounds(SoundEventType.MUSIC).size());
    }

    @Test
    void choicesFollowAChangeInTheGameTrack() {
        var info = info();
        info.update(musicRule(false, "one"));
        assertEquals(Set.of(id("one"), id("first")), chosen(info, Optional.of(music(id("first")))));
        assertEquals(Set.of(id("one"), id("second")), chosen(info, Optional.of(music(id("second")))));
    }

    @Test
    void choicesFollowAnUpdate() {
        var info = info();
        var vanilla = Optional.of(music(id("vanilla")));
        info.update(musicRule(false, "one"));
        assertEquals(Set.of(id("one"), id("vanilla")), chosen(info, vanilla));
        info.update(musicRule(false, "two"));
        assertEquals(Set.of(id("one"), id("two"), id("vanilla")), chosen(info, vanilla));
    }
}

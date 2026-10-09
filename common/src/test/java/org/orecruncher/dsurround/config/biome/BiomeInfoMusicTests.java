package org.orecruncher.dsurround.config.biome;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
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
 * The background music a biome offers: the configured music alongside the track the game offers for the biome.
 * Which of them plays is up to {@link BiomeMusicSelector}.
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

    private static final ISoundLibrary SOUNDS = Fakes.of(ISoundLibrary.class, Map.of(
            "getSoundFactoryOrDefault", args -> soundFactory((Identifier) args[0]),
            "getSoundFactoryForMusic", args -> soundFactory(location((Music) args[0]))));

    private static final IConditionEvaluator CONDITIONS = Fakes.of(IConditionEvaluator.class, Map.of(
            "eval", args -> 0D,
            "check", args -> true));

    static BiomeInfo info() {
        var services = new ConfigServices(new RecordingLog(), SOUNDS, Fakes.of(ITagLibrary.class, Map.of()), CONDITIONS);
        return new BiomeInfo(1, id("biome"), "biome", BiomeTraits.of(), services);
    }

    static BiomeConfigRule musicRule(boolean clearSounds, String... sounds) {
        var acoustics = new ArrayList<AcousticConfig>();
        for (var s : sounds)
            acoustics.add(new AcousticConfig(id(s), Script.TRUE, WeightValue.of(10), SoundEventType.MUSIC));
        return new BiomeConfigRule(Script.TRUE, Optional.empty(), 0, false, List.of(), clearSounds, false,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), acoustics);
    }

    private static Set<Identifier> choices(BiomeInfo info, Optional<Music> vanilla) {
        return info.getMusicChoices(vanilla).stream()
                .map(e -> e.getAcoustic().getLocation())
                .collect(Collectors.toSet());
    }

    @Test
    void noMusicAnywhereGivesNoChoices() {
        assertTrue(info().getMusicChoices(Optional.empty()).isEmpty());
    }

    @Test
    void withoutConfiguredMusicTheGameTrackIsTheChoice() {
        assertEquals(Set.of(id("vanilla")), choices(info(), Optional.of(music(id("vanilla")))));
    }

    @Test
    void configuredMusicAloneWhenTheGameOffersNone() {
        var info = info();
        info.update(musicRule(false, "one", "two"));
        assertEquals(Set.of(id("one"), id("two")), choices(info, Optional.empty()));
    }

    @Test
    void configuredMusicIsOfferedAlongsideTheGameTrack() {
        var info = info();
        info.update(musicRule(false, "one"));
        assertEquals(Set.of(id("one"), id("vanilla")), choices(info, Optional.of(music(id("vanilla")))));
    }

    @Test
    void clearingSoundsKeepsTheGameTrack() {
        var info = info();
        info.update(musicRule(false, "one"));
        info.update(musicRule(true, "two"));
        assertEquals(Set.of(id("two"), id("vanilla")), choices(info, Optional.of(music(id("vanilla")))));
    }

    @Test
    void theGameTrackIsNotListedWithTheConfiguredMusic() {
        var info = info();
        info.update(musicRule(false, "one"));
        info.getMusicChoices(Optional.of(music(id("vanilla"))));
        assertEquals(1, info.getSounds(SoundEventType.MUSIC).size());
    }

    @Test
    void choicesFollowAChangeInTheGameTrack() {
        var info = info();
        info.update(musicRule(false, "one"));
        assertEquals(Set.of(id("one"), id("first")), choices(info, Optional.of(music(id("first")))));
        assertEquals(Set.of(id("one"), id("second")), choices(info, Optional.of(music(id("second")))));
    }

    @Test
    void choicesFollowAnUpdate() {
        var info = info();
        var vanilla = Optional.of(music(id("vanilla")));
        info.update(musicRule(false, "one"));
        assertEquals(Set.of(id("one"), id("vanilla")), choices(info, vanilla));
        info.update(musicRule(false, "two"));
        assertEquals(Set.of(id("one"), id("two"), id("vanilla")), choices(info, vanilla));
    }

    @Test
    void theSameChoicesAreReturnedWhileTheGameTrackIsTheSame() {
        // How the selector knows the choices haven't changed
        var info = info();
        info.update(musicRule(false, "one"));
        var vanilla = music(id("vanilla"));
        assertSame(info.getMusicChoices(Optional.of(vanilla)), info.getMusicChoices(Optional.of(vanilla)));
        assertSame(info.getMusicChoices(Optional.empty()), info.getMusicChoices(Optional.empty()));
    }

    @Test
    void newChoicesAfterAChangeInTheGameTrackOrAnUpdate() {
        var info = info();
        var first = info.getMusicChoices(Optional.of(music(id("first"))));
        var second = info.getMusicChoices(Optional.of(music(id("second"))));
        assertNotSame(first, second);
        info.update(musicRule(false, "one"));
        assertNotSame(second, info.getMusicChoices(Optional.of(music(id("second")))));
    }
}

package org.orecruncher.dsurround.lib.music;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The name the game's "now playing" toast and pause screen show for a track.
 */
public class NowPlayingNameTests {

    private static final Set<String> TRANSLATED = Set.of("music.game.sweden");

    @Test
    void nothingPlaying() {
        assertNull(NowPlayingName.of(null, TRANSLATED::contains, Component.literal("Title"), null));
    }

    @Test
    void keepsTheGamesKeyWhenItHasATranslation() {
        // The game turns "/" into "." before looking the key up
        assertEquals("music/game/sweden", NowPlayingName.of("music/game/sweden", TRANSLATED::contains, Component.literal("Sweden"), Component.literal("C418")));
    }

    @Test
    void keepsTheGamesKeyWhenThereIsNoTitle() {
        assertEquals("dsurround_seasons.music/spring1", NowPlayingName.of("dsurround_seasons.music/spring1", TRANSLATED::contains, null, Component.literal("Vivaldi")));
    }

    @Test
    void namesUntranslatedTracksByAuthorAndTitle() {
        var name = NowPlayingName.of("dsurround_seasons.music/spring1", TRANSLATED::contains,
                Component.literal("The Four Seasons - Early Spring"), Component.literal("Antonio Vivaldi"));
        assertEquals("Antonio Vivaldi - The Four Seasons - Early Spring", name);
    }

    @Test
    void namesByTitleAloneWithoutCredits() {
        assertEquals("Spring", NowPlayingName.of("dsurround_seasons.music/spring1", TRANSLATED::contains, Component.literal("Spring"), null));
    }

    @Test
    void namesSurviveBeingReadAsAKey() {
        // Read as a format, "%%" shows as "%"; and the game would turn "/" into "."
        var name = NowPlayingName.of("x.music/a", TRANSLATED::contains, Component.literal("100% AC/DC"), null);
        assertEquals("100%% AC∕DC", name);
        assertEquals("100% AC∕DC", Component.translatable(name.replace('/', '.')).getString());
    }
}

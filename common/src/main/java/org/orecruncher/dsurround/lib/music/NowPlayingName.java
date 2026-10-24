package org.orecruncher.dsurround.lib.music;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * The name the game shows for the current track, in its "now playing" toast and on the pause screen. The game makes
 * a translation key from the sound file's location ("dsurround_seasons:music/spring1" becomes
 * "dsurround_seasons.music.spring1"), which only its own tracks have a translation for; for the others the key itself
 * would be shown.
 */
final class NowPlayingName {

    private NowPlayingName() {
    }

    /**
     * @param key           the game's key for the track, or null when nothing is playing
     * @param isTranslated  whether the language has a translation for a key
     * @param title         the track's title from its sound metadata, if it has one
     * @param author        the first author credited, if any
     * @return the game's key when it has a translation or there is no title; otherwise the title, after the author as
     * the game names its own tracks ("C418 - Sweden")
     */
    static @Nullable String of(@Nullable String key, Predicate<String> isTranslated, @Nullable Component title, @Nullable Component author) {
        // The game turns "/" into "." before looking the key up
        if (key == null || title == null || isTranslated.test(key.replace('/', '.')))
            return key;

        var name = author == null ? title.getString() : author.getString() + " - " + title.getString();
        // The game looks the name up as a key; a missing key is shown as written, but still read as a format ("%"),
        // and "/" would still become "."
        return name.replace("%", "%%").replace('/', '∕');
    }
}

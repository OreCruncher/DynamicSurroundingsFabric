// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.profiling;

import dev.architectury.platform.Mod;
import dev.architectury.platform.Platform;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One installed mod, as a recording names it (see {@link ModEvent}): its id and version, which are what a mod page,
 * a crash report and a launcher show. Nothing else of the mod is taken: not its name, authors or description, and
 * never where its jar is (a path names the player's account). The loader, Minecraft and Java are in the list as the
 * loaders list them. Uses nothing from {@code jdk.jfr}.
 *
 * @param id      the mod's id
 * @param version the mod's version, as its metadata gives it
 */
record ProfileMod(String id, String version) {

    /**
     * The longest id or version written; anything longer is cut
     */
    static final int MAX_LENGTH = 64;

    /**
     * The installed mods, sorted by id
     */
    static List<ProfileMod> capture() {
        return of(Platform.getMods().stream().collect(Collectors.toMap(Mod::getModId, Mod::getVersion, (a, b) -> a)));
    }

    /**
     * The mods from their ids and versions, sorted by id, each cleaned (see {@link #clean})
     */
    static List<ProfileMod> of(Map<String, String> versions) {
        return versions.entrySet().stream()
                .map(e -> new ProfileMod(clean(e.getKey()), clean(e.getValue())))
                .sorted(Comparator.comparing(ProfileMod::id))
                .toList();
    }

    /**
     * Metadata is the mod's own text: printable ASCII only, without path separators or drive colons (no version is a
     * path, but a broken build's could hold one), cut at {@link #MAX_LENGTH}. What is left out becomes '?'.
     */
    static String clean(String text) {
        if (text == null || text.isBlank())
            return "?";
        var cleaned = new StringBuilder(Math.min(text.length(), MAX_LENGTH));
        for (int i = 0; i < text.length() && cleaned.length() < MAX_LENGTH; i++) {
            char c = text.charAt(i);
            cleaned.append(c >= 0x20 && c < 0x7F && c != '/' && c != '\\' && c != ':' ? c : '?');
        }
        return cleaned.toString().strip();
    }
}

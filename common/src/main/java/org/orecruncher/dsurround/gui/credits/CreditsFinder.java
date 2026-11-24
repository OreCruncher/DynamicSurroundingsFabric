// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.gui.credits;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Finds the credits files of the active packs, for the credits screen.
 * <ul>
 *   <li>A resource pack (one the player, a server or a world added) keeps its credits at its root, as
 *       {@code CREDITS.md} or {@code credits.md}, where they are on its download page too; failing that, in
 *       {@code assets/<namespace>/credits.md}. It is listed once, by the pack's title.</li>
 *   <li>A mod keeps them in {@code assets/<namespace>/credits.md}, as Dynamic Surroundings does (a mod's pack root
 *       isn't always its jar's: on NeoForge mods' resources are merged), listed by the mod's name.</li>
 * </ul>
 * Only active packs are asked, so a pack turned off drops out. The order is the pack screen's: highest priority
 * first. A file with the same text as one already listed (the mod's own copy, read first; a pack listed twice) is
 * left out, as is an empty or unreadable one.
 */
public final class CreditsFinder {

    static final List<String> ROOT_FILES = List.of("CREDITS.md", "credits.md");
    static final String ASSET_FILE = "credits.md";

    private CreditsFinder() {
    }

    /**
     * @param first   an entry listed before the packs (the mod's own credits), if any
     * @param packs   the active packs, lowest priority first, as the resource manager lists them
     * @param modName a mod's display name by its id, empty if no mod has that id
     */
    public static List<CreditsEntry> find(Optional<CreditsEntry> first, List<PackResources> packs, Function<String, Optional<String>> modName) {
        var entries = new ArrayList<CreditsEntry>();
        Set<String> seen = new HashSet<>();
        first.ifPresent(entry -> add(entries, seen, entry));

        for (int i = packs.size() - 1; i >= 0; i--) {
            var pack = packs.get(i);
            if (isResourcePack(pack)) {
                resourcePackCredits(pack).ifPresent(text -> add(entries, seen, new CreditsEntry(pack.location().title(), text)));
            } else {
                // A mod pack can hold several mods (Fabric groups them); each namespace is asked, in a fixed order
                for (var namespace : new TreeSet<>(pack.getNamespaces(PackType.CLIENT_RESOURCES))) {
                    var name = modName.apply(namespace).<Component>map(Component::literal).orElse(pack.location().title());
                    assetCredits(pack, namespace).ifPresent(text -> add(entries, seen, new CreditsEntry(name, text)));
                }
            }
        }
        return entries;
    }

    /**
     * A pack the player, a server or a world added, as against the game's own and mods' (built in, or a loader's
     * own source).
     */
    static boolean isResourcePack(PackResources pack) {
        var source = pack.location().source();
        return source == PackSource.DEFAULT || source == PackSource.SERVER || source == PackSource.WORLD;
    }

    private static Optional<String> resourcePackCredits(PackResources pack) {
        for (var name : ROOT_FILES) {
            var text = read(safely(() -> pack.getRootResource(name)));
            if (text.isPresent())
                return text;
        }
        for (var namespace : new TreeSet<>(pack.getNamespaces(PackType.CLIENT_RESOURCES))) {
            var text = assetCredits(pack, namespace);
            if (text.isPresent())
                return text;
        }
        return Optional.empty();
    }

    private static Optional<String> assetCredits(PackResources pack, String namespace) {
        var id = Identifier.tryBuild(namespace, ASSET_FILE);
        if (id == null)
            return Optional.empty();
        return read(safely(() -> pack.getResource(PackType.CLIENT_RESOURCES, id)));
    }

    private static void add(List<CreditsEntry> entries, Set<String> seen, CreditsEntry entry) {
        // Line endings don't make a file different
        if (seen.add(entry.markdown().replace("\r\n", "\n").strip()))
            entries.add(entry);
    }

    // A pack's lookup can throw (a broken zip); that pack's file is then left out
    private static IoSupplier<InputStream> safely(java.util.function.Supplier<IoSupplier<InputStream>> lookup) {
        try {
            return lookup.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Optional<String> read(IoSupplier<InputStream> supplier) {
        if (supplier == null)
            return Optional.empty();
        try (var stream = supplier.get()) {
            var text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            // A BOM would show as a stray character
            if (text.startsWith("﻿"))
                text = text.substring(1);
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * One entry in the credits screen's list: what it is called there, and its Markdown.
     */
    public record CreditsEntry(Component name, String markdown) {
    }
}

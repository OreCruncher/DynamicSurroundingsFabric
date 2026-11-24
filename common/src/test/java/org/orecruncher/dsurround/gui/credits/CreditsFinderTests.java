// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.gui.credits;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.testing.Fakes;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class CreditsFinderTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final Function<String, Optional<String>> MODS = id -> switch (id) {
        case "dsurround" -> Optional.of("Dynamic Surroundings");
        case "othermod" -> Optional.of("Other Mod");
        default -> Optional.empty();
    };

    private static final CreditsFinder.CreditsEntry OWN = new CreditsFinder.CreditsEntry(Component.literal("Dynamic Surroundings"), "# Ours\n");

    /**
     * A pack with the given root files and {@code namespace -> credits.md} asset files.
     */
    private static PackResources pack(String title, PackSource source, Map<String, String> rootFiles, Map<String, String> assetFiles) {
        var location = new PackLocationInfo(title, Component.literal(title), source, Optional.empty());
        return Fakes.of(PackResources.class, Map.of(
                "location", args -> location,
                "getRootResource", args -> supplier(rootFiles.get(((String[]) args[0])[0])),
                "getNamespaces", args -> assetFiles.keySet(),
                "getResource", args -> {
                    var id = (Identifier) args[1];
                    return id.getPath().equals(CreditsFinder.ASSET_FILE) && args[0] == PackType.CLIENT_RESOURCES ? supplier(assetFiles.get(id.getNamespace())) : null;
                }));
    }

    private static IoSupplier<InputStream> supplier(String text) {
        return text == null ? null : () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> names(List<CreditsFinder.CreditsEntry> entries) {
        return entries.stream().map(e -> e.name().getString()).toList();
    }

    @Test
    void oursFirstThenResourcePacksHighestFirstThenMods() {
        var vanilla = pack("vanilla", PackSource.BUILT_IN, Map.of(), Map.of());
        var mods = pack("Mod Resources", PackSource.BUILT_IN, Map.of(), Map.of("dsurround", "# Ours\n", "othermod", "theirs"));
        var seasons = pack("Seasons", PackSource.DEFAULT, Map.of("CREDITS.md", "seasons"), Map.of());
        var extended = pack("Extended", PackSource.DEFAULT, Map.of("CREDITS.md", "extended"), Map.of());

        // Lowest priority first, as the resource manager lists them
        var found = CreditsFinder.find(Optional.of(OWN), List.of(vanilla, mods, extended, seasons), MODS);
        assertEquals(List.of("Dynamic Surroundings", "Seasons", "Extended", "Other Mod"), names(found));
        assertEquals("theirs", found.get(3).markdown());
    }

    @Test
    void ourOwnCopyInOurPackIsNotListedTwice() {
        // Line endings alone don't make it another file
        var mods = pack("Mods", PackSource.BUILT_IN, Map.of(), Map.of("dsurround", "# Ours\r\n"));
        assertEquals(List.of("Dynamic Surroundings"), names(CreditsFinder.find(Optional.of(OWN), List.of(mods), MODS)));
    }

    @Test
    void resourcePackRootFileWinsOverItsAssetsAndTakesEitherCase() {
        var both = pack("Both", PackSource.DEFAULT, Map.of("CREDITS.md", "root"), Map.of("both", "asset"));
        var lower = pack("Lower", PackSource.SERVER, Map.of("credits.md", "lower"), Map.of());
        var assetsOnly = pack("Assets", PackSource.WORLD, Map.of(), Map.of("ns", "asset only"));

        var found = CreditsFinder.find(Optional.empty(), List.of(assetsOnly, lower, both), MODS);
        assertEquals(List.of("Both", "Lower", "Assets"), names(found));
        assertEquals(List.of("root", "lower", "asset only"), found.stream().map(CreditsFinder.CreditsEntry::markdown).toList());
    }

    @Test
    void resourcePackIsListedByItsTitleEvenWhenItsNamespaceIsAMods() {
        // A pack retexturing a mod keeps its own name
        var retexture = pack("Retexture", PackSource.DEFAULT, Map.of(), Map.of("othermod", "retexture credits"));
        assertEquals(List.of("Retexture"), names(CreditsFinder.find(Optional.empty(), List.of(retexture), MODS)));
    }

    @Test
    void modPackRootFileIsIgnored() {
        // A mod pack's root may be another mod's jar root, or a loader's group of them, so it would be mislabeled
        var mods = pack("Fabric Mods", PackSource.BUILT_IN, Map.of("CREDITS.md", "someone's"), Map.of());
        assertTrue(CreditsFinder.find(Optional.empty(), List.of(mods), MODS).isEmpty());
    }

    @Test
    void namespaceThatIsNoModIsListedByThePackTitle() {
        var pack = pack("Some Pack", PackSource.BUILT_IN, Map.of(), Map.of("notamod", "credits"));
        assertEquals(List.of("Some Pack"), names(CreditsFinder.find(Optional.empty(), List.of(pack), MODS)));
    }

    @Test
    void blankBrokenAndBomFiles() {
        var blank = pack("Blank", PackSource.DEFAULT, Map.of("CREDITS.md", " \n\t\n"), Map.of());
        var bom = pack("Bom", PackSource.DEFAULT, Map.of("CREDITS.md", "﻿# Title"), Map.of());
        var location = new PackLocationInfo("Broken", Component.literal("Broken"), PackSource.DEFAULT, Optional.empty());
        PackResources broken = Fakes.of(PackResources.class, Map.of(
                "location", args -> location,
                "getRootResource", args -> (IoSupplier<InputStream>) () -> {
                    throw new java.io.IOException("bad zip");
                },
                "getNamespaces", args -> Set.of()));
        PackResources throwing = Fakes.of(PackResources.class, Map.of(
                "location", args -> location,
                "getRootResource", args -> {
                    throw new IllegalStateException("closed");
                },
                "getNamespaces", args -> Set.of()));

        var found = CreditsFinder.find(Optional.empty(), List.of(blank, broken, throwing, bom), MODS);
        assertEquals(List.of("Bom"), names(found));
        assertEquals("# Title", found.getFirst().markdown());
    }

    @Test
    void identicalFilesInTwoPacksAreListedOnce() {
        var files = new HashMap<String, String>();
        files.put("CREDITS.md", "same");
        var a = pack("A", PackSource.DEFAULT, files, Map.of());
        var b = pack("B", PackSource.DEFAULT, files, Map.of());
        // The higher one (listed last) is kept
        assertEquals(List.of("B"), names(CreditsFinder.find(Optional.empty(), List.of(a, b), MODS)));
    }
}

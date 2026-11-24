// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.gui.credits;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.orecruncher.dsurround.lib.markdown.MarkdownParser;
import org.orecruncher.dsurround.lib.markdown.Options;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The credits files the credits screen shows (the mod's, and each pack's in {@code packs/}) render with nothing of
 * their Markdown left over. The parser shows what it can't read as written, so a link broken across lines, inline
 * code or an unsupported list would appear as raw text in game.
 */
class CreditsDocumentTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<Path> documents() throws IOException {
        var documents = new ArrayList<Path>();
        documents.add(Path.of("../CREDITS.md"));
        try (Stream<Path> packs = Files.list(Path.of("../packs"))) {
            packs.map(pack -> pack.resolve("CREDITS.md")).filter(Files::isRegularFile).sorted().forEach(documents::add);
        }
        return documents;
    }

    @TestFactory
    Stream<DynamicTest> rendersWithoutLeftoverMarkdown() throws IOException {
        var documents = documents();
        // The mod's and the two packs'; nothing to check must fail
        assertTrue(documents.size() >= 3, () -> "found only " + documents);
        return documents.stream().map(path -> DynamicTest.dynamicTest(path.toString(), () -> {
            var text = MarkdownParser.markdownToComponent(Files.readString(path), Options.UNIFORM).orElseThrow().getString();
            var lines = text.split("\n");
            for (int i = 0; i < lines.length; i++) {
                var line = lines[i];
                var where = path + ", rendered line " + (i + 1) + ": " + line;
                assertFalse(line.contains("]("), () -> "a link that didn't parse (one split over lines?): " + where);
                assertFalse(line.contains("`"), () -> "inline code, which isn't supported: " + where);
                assertFalse(line.matches("^\\s*#.*"), () -> "a heading that didn't parse: " + where);
                assertFalse(line.matches(".*(^|\\s)\\*\\S.*") || line.matches(".*\\S\\*(\\s|$).*"),
                        () -> "an emphasis marker left over: " + where);
            }
        }));
    }
}

package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orecruncher.dsurround.lib.threading.RecordingLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DiskResourceFinderTests {

    private static final Codec<Map<String, Integer>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT);

    @TempDir
    Path root;

    private void write(String relative, String content) throws IOException {
        var file = this.root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private DiskResourceFinder finder(String... installedMods) {
        var installed = Set.of(installedMods);
        return new DiskResourceFinder(new RecordingLog(), this.root, installed::contains);
    }

    @Test
    void findsAFileInAnInstalledModsFolder() throws IOException {
        write("dsurround/tags/block/effects/fireflies.json", "{\"a\": 1}");
        var found = finder("dsurround").find(CODEC, "tags/block/effects/fireflies");

        assertEquals(1, found.size());
        var resource = found.iterator().next();
        assertEquals("dsurround", resource.namespace());
        assertEquals(Map.of("a", 1), resource.resourceContent());
    }

    @Test
    void addsTheJsonExtensionOnlyWhenMissing() throws IOException {
        write("dsurround/blocks.json", "{\"b\": 2}");
        var finder = finder("dsurround");
        assertEquals(1, finder.find(CODEC, "blocks").size());
        assertEquals(1, finder.find(CODEC, "blocks.json").size());
    }

    @Test
    void ignoresFoldersOfModsThatArentInstalled() throws IOException {
        write("dsurround/blocks.json", "{\"a\": 1}");
        write("othermod/blocks.json", "{\"b\": 2}");
        var found = finder("dsurround").find(CODEC, "blocks");
        assertEquals(1, found.size());
        assertEquals("dsurround", found.iterator().next().namespace());
    }

    @Test
    void findsTheFileInEachInstalledMod() throws IOException {
        write("dsurround/blocks.json", "{\"a\": 1}");
        write("othermod/blocks.json", "{\"b\": 2}");
        var namespaces = finder("dsurround", "othermod").find(CODEC, "blocks").stream()
                .map(DiscoveredResource::namespace)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("dsurround", "othermod"), namespaces);
    }

    @Test
    void nothingWhenTheFileIsntThere() throws IOException {
        write("dsurround/other.json", "{\"a\": 1}");
        assertTrue(finder("dsurround").find(CODEC, "blocks").isEmpty());
    }

    @Test
    void nothingWhenTheLocationDoesntExist() {
        // The usual case: most players have no config folder of their own
        var finder = new DiskResourceFinder(new RecordingLog(), this.root.resolve("missing"), id -> true);
        assertTrue(finder.find(CODEC, "blocks").isEmpty());
    }

    @Test
    void aFileThatDoesntDecodeIsSkipped() throws IOException {
        write("dsurround/broken.json", "{ not json");
        write("othermod/broken.json", "{\"a\": 1}");
        var found = assertDoesNotThrow(() -> finder("dsurround", "othermod").find(CODEC, "broken"));
        // The good one is still found
        assertEquals(1, found.size());
        assertEquals("othermod", found.iterator().next().namespace());
    }
}

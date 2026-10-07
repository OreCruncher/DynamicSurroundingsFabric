package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orecruncher.dsurround.lib.threading.RecordingLog;
import org.orecruncher.dsurround.testing.Fakes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The resource finders: the mod jars' and resource packs' config folders, sounds.json across every namespace, and
 * the installed mods' data.
 */
public class ResourceFinderTests {

    private static final Codec<Map<String, String>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING);

    /**
     * A resource manager holding the given files, by resource location, each with one or more versions (one per pack
     * that has it). Lists the files under a folder as the real one does, and counts how often it is asked.
     */
    private static final class Packs {
        final Map<ResourceLocation, List<Resource>> files = new LinkedHashMap<>();
        int listings;

        Packs add(String location, String... contents) {
            var stack = this.files.computeIfAbsent(ResourceLocation.parse(location), l -> new ArrayList<>());
            for (var content : contents)
                stack.add(new Resource(null, () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
            return this;
        }

        ResourceManager manager() {
            return Fakes.of(ResourceManager.class, Map.of(
                    "listResourceStacks", args -> {
                        this.listings++;
                        // Lists the files in a folder, as the real one does; a file's own path lists nothing, as
                        // with NeoForge's mod packs
                        var folder = (String) args[0];
                        var result = new LinkedHashMap<ResourceLocation, List<Resource>>();
                        this.files.forEach((location, stack) -> {
                            if (location.getPath().startsWith(folder + "/"))
                                result.put(location, stack);
                        });
                        return result;
                    },
                    "getNamespaces", args -> this.files.keySet().stream().map(ResourceLocation::getNamespace).collect(Collectors.toSet()),
                    "getResourceStack", args -> this.files.getOrDefault((ResourceLocation) args[0], List.of())));
        }
    }

    private static final Set<String> INSTALLED = Set.of("dsurround", "minecraft", "biomesoplenty");

    private static ModConfigResourceFinder configFinder(Packs packs) {
        return new ModConfigResourceFinder(new RecordingLog(), packs.manager(), "dsconfigs", INSTALLED::contains);
    }

    private static Set<String> namespaces(java.util.Collection<? extends DiscoveredResource<?>> found) {
        return found.stream().map(DiscoveredResource::namespace).collect(Collectors.toSet());
    }

    // ---- Config folders in mod jars and resource packs

    @Test
    void findsAConfigFileByItsPath() {
        var packs = new Packs().add("dsurround:dsconfigs/blocks.json", "{\"a\": \"1\"}");
        var found = configFinder(packs).find(CODEC, "blocks");
        assertEquals(1, found.size());
        assertEquals(Map.of("a", "1"), found.iterator().next().resourceContent());
    }

    @Test
    void matchesTheWholePathNotJustTheEnd() {
        // These used to be found for "blocks.json", as their paths end with it
        var packs = new Packs()
                .add("dsurround:dsconfigs/old/blocks.json", "{\"old\": \"x\"}")
                .add("dsurround:dsconfigs/myblocks.json", "{\"my\": \"x\"}");
        assertTrue(configFinder(packs).find(CODEC, "blocks.json").isEmpty());
    }

    @Test
    void findsTagFilesInSubfolders() {
        var packs = new Packs().add("biomesoplenty:dsconfigs/tags/block/effects/fireflies.json", "{\"t\": \"1\"}");
        assertEquals(1, configFinder(packs).find(CODEC, "tags/block/effects/fireflies").size());
    }

    @Test
    void skipsConfigurationForModsThatArentInstalled() {
        var packs = new Packs()
                .add("dsurround:dsconfigs/biomes.json", "{\"a\": \"1\"}")
                .add("biomesoplenty:dsconfigs/biomes.json", "{\"b\": \"2\"}")
                .add("natures_spirit:dsconfigs/biomes.json", "{\"c\": \"3\"}");
        assertEquals(Set.of("dsurround", "biomesoplenty"), namespaces(configFinder(packs).find(CODEC, "biomes")));
    }

    @Test
    void readsEveryPacksVersionOfAFile() {
        // The mod jar and a resource pack can both have one
        var packs = new Packs().add("dsurround:dsconfigs/blocks.json", "{\"jar\": \"1\"}", "{\"pack\": \"2\"}");
        assertEquals(2, configFinder(packs).find(CODEC, "blocks").size());
    }

    @Test
    void aBrokenFileIsSkippedAndTheRestLoad() {
        var packs = new Packs()
                .add("dsurround:dsconfigs/blocks.json", "{ not json")
                .add("minecraft:dsconfigs/blocks.json", "{\"a\": \"1\"}");
        var found = assertDoesNotThrow(() -> configFinder(packs).find(CODEC, "blocks"));
        assertEquals(Set.of("minecraft"), namespaces(found));
    }

    @Test
    void readsUtf8() {
        var packs = new Packs().add("dsurround:dsconfigs/names.json", "{\"name\": \"Café – ブロック\"}");
        assertEquals("Café – ブロック", configFinder(packs).find(CODEC, "names").iterator().next().resourceContent().get("name"));
    }

    @Test
    void listsTheFilesOnceForAnyNumberOfLookups() {
        var packs = new Packs().add("dsurround:dsconfigs/blocks.json", "{}");
        var finder = configFinder(packs);
        for (int i = 0; i < 10; i++)
            finder.find(CODEC, "blocks");
        assertEquals(1, packs.listings);
    }

    // ---- Assets in every namespace

    @Test
    void assetsAreFoundInEveryNamespaceInstalledOrNot() {
        // Like vanilla, a resource pack's own namespace counts: it can add sounds of its own
        var packs = new Packs()
                .add("minecraft:sounds.json", "{\"a\": \"1\"}")
                .add("mypack:sounds.json", "{\"b\": \"2\"}");
        var found = new ClientResourceFinder(new RecordingLog(), packs.manager()).find(CODEC, "sounds.json");
        assertEquals(Set.of("minecraft", "mypack"), namespaces(found));
    }

    @Test
    void assetsAreLookedUpNotListed() {
        // Listing "sounds.json" finds nothing in packs that only list folders (NeoForge 26.2's mod packs), so none of
        // the mod's own sounds would be known. Every pack's version of the file is read.
        var packs = new Packs()
                .add("dsurround:sounds.json", "{\"jar\": \"1\"}", "{\"pack\": \"2\"}")
                .add("dsurround:sounds/other.json", "{\"c\": \"3\"}");
        var found = new ClientResourceFinder(new RecordingLog(), packs.manager()).find(CODEC, "sounds.json");
        assertEquals(2, found.size());
        assertEquals(Set.of("dsurround"), namespaces(found));
    }

    // ---- Installed mods' data

    @TempDir
    Path folder;

    private Path dataRoot(String mod, String relative, String content) throws IOException {
        var root = this.folder.resolve(mod).resolve("data");
        var file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return root;
    }

    @Test
    void findsDataInEachInstalledMod() throws IOException {
        var first = dataRoot("modone", "c/tags/block/glass_blocks.json", "{\"a\": \"1\"}");
        var second = dataRoot("modtwo", "c/tags/block/glass_blocks.json", "{\"b\": \"2\"}");
        var finder = new ServerResourceFinder(new RecordingLog(), () -> List.of(first, second));

        var found = finder.find(CODEC, "c:tags/block/glass_blocks");
        assertEquals(2, found.size());
        // Recorded under the tag's namespace
        assertEquals(Set.of("c"), namespaces(found));
    }

    @Test
    void nothingWhereNoInstalledModHasTheFile() throws IOException {
        var root = dataRoot("modone", "c/tags/block/other.json", "{}");
        var finder = new ServerResourceFinder(new RecordingLog(), () -> List.of(root));
        assertTrue(finder.find(CODEC, "c:tags/block/glass_blocks").isEmpty());
    }

    @Test
    void aBadLocationFindsNothingRatherThanFailing() {
        var finder = new ServerResourceFinder(new RecordingLog(), List::of);
        assertTrue(assertDoesNotThrow(() -> finder.find(CODEC, "Not A Location!")).isEmpty());
    }

    @Test
    void theJsonExtensionIsAddedOnlyWhenMissing() {
        assertEquals("a/b.json", AbstractResourceFinder.withJsonExtension("a/b"));
        assertEquals("a/b.json", AbstractResourceFinder.withJsonExtension("a/b.json"));
    }
}

package org.orecruncher.dsurround.config.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagFile;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.sound.SoundFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feeds the decoding of the configuration files in {@code dsconfigs} (biomes, blocks, dimensions, sound factories,
 * sound mappings and tags) large numbers of hand-edited versions of the files the mod ships, and checks what must
 * hold for every one of them:
 * <ul>
 *     <li>Nothing escapes a codec as an exception. The loader catches it, but then the whole file is lost; a
 *     problem must come back as an error in the result, so only the entry it is in is affected.</li>
 *     <li>A bad entry costs at most itself: every other entry in the file decodes exactly as it did before.</li>
 * </ul>
 * Each case takes one entry of one shipped file and edits it: values replaced with odd ones (wrong types, NaN, huge
 * numbers, deep nesting), strings garbled (block state specifications, scripts, ids, colours), members removed,
 * unknown ones added, values wrapped in arrays.
 * <p>
 * Runs are reproducible. Case {@code i} always uses {@code new Random(seed + i)}. When a case fails, the edited entry
 * is shrunk to a minimal one that still fails and printed as JSON, ready to paste into a regression test in
 * {@link ConfigDataTests}. To explore further, change {@link #CASES} and {@link #SEED} (or pass
 * {@code -Ddsconfig.fuzz.cases} and {@code -Ddsconfig.fuzz.seed}, if the build passes system properties to the test
 * JVM).
 */
class DsConfigFuzzTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final int CASES = Integer.getInteger("dsconfig.fuzz.cases", 3_000);
    private static final long SEED = Long.getLong("dsconfig.fuzz.seed", 20261016L);

    /**
     * Values a hand-edited file might hold anywhere, as lenient JSON text, as the loader reads it
     */
    private static final String[] ODD_VALUES = {
            "null", "true", "false", "0", "-1", "1", "0.5", "-0.0", "2147483647", "2147483648", "-2147483649",
            "9223372036854775808", "99999999999999999999999", "1e308", "1e400", "-1e400", "1e-400", "4.9e-324",
            "NaN", "-NaN", "Infinity", "-Infinity", "0x10", "010", ".5",
            "\"\"", "\" \"", "\"5\"", "\"true\"", "\"NaN\"", "\"#\"", "\"#c:\"", "\"minecraft:\"", "\":\"",
            "\"a:b:c\"", "\"Minecraft:Stone\"", "\"minecraft:stone\"", "\"#minecraft:logs\"", "\"minecraft:air\"",
            "\"minecraft:stone[\"", "\"minecraft:stone[]\"", "\"minecraft:stone[a=1]\"", "\"minecraft:oak_log[axis=up]\"",
            "\"\\u0000\"", "\"\\ud83d\"", "\"\uD83D\uDE00\"", "\"#FFF\"", "\"#GGGGGG\"", "\"#FFFFFFFFF\"",
            "\"(\"", "\")\"", "\"1 +\"", "\"a && || b\"", "\"'unterminated\"", "\"x.y.z()\"",
            "[]", "[1]", "[null]", "[[]]", "{}", "{\"a\": 1}", "{\"id\": 5}"
    };

    /**
     * Pieces of text dropped into strings: what block state specifications, scripts, ids and colours are made of
     */
    private static final String[] STRING_FRAGMENTS = {
            "[", "]", "=", ",", ":", "#", "@", "!", "(", ")", "'", "\"", "\\", ".", "..", "&&", "||", "+", "-",
            "*", "/", "%", "?", "<", ">", "==", "0", "9", "1e9", " ", "\t", "\n", "\u0000", "é", "\uD83D",
            "\uD83D\uDE00", "A", "true", "null", "minecraft", "c:", "#c:"
    };

    private static final Gson GSON = new Gson();

    /**
     * A kind of file: how it is decoded, and where its entries are
     */
    private record Kind(String name, Codec<?> codec, Function<Object, List<?>> decodedEntries,
                        Function<JsonElement, @Nullable JsonArray> entries) {
    }

    private static final Kind BIOMES = listKind("biomes", BiomeConfigRule.CODEC);
    private static final Kind BLOCKS = listKind("blocks", BlockConfigRule.CODEC);
    private static final Kind DIMENSIONS = listKind("dimensions", DimensionConfigRule.CODEC);
    private static final Kind SOUND_FACTORIES = listKind("sound_factories", SoundFactory.CODEC);
    private static final Kind SOUND_MAPPINGS = listKind("sound_mappings", SoundMappingConfigRule.CODEC);
    private static final Kind TAGS = new Kind("tags", TagFile.CODEC, tag -> ((TagFile) tag).entries(),
            root -> root instanceof JsonObject o && o.get("values") instanceof JsonArray a ? a : null);

    private static Kind listKind(String name, Codec<?> codec) {
        return new Kind(name, Codec.list(codec), list -> (List<?>) list, root -> root instanceof JsonArray a ? a : null);
    }

    /**
     * A file the mod ships
     */
    private record Source(String path, Kind kind, JsonElement root) {
    }

    private static final List<List<Source>> SOURCES_BY_KIND = loadSources();

    // ---- The tests -------------------------------------------------------------------------------------------

    @Test
    void editedEntriesStayInTheirEntry() {
        for (int i = 0; i < CASES; i++) {
            var random = new Random(SEED + i);
            var kind = pick(random, SOURCES_BY_KIND);
            var source = pick(random, kind);
            var entries = source.kind().entries().apply(source.root());
            assertNotNull(entries, source.path());
            if (entries.isEmpty())
                continue;

            int index = random.nextInt(entries.size());
            var edited = edit(random, entries.get(index));
            var problem = check(source, index, edited);
            if (problem != null) {
                var minimal = shrink(edited, candidate -> check(source, index, candidate) != null);
                fail("case %d (seed %d): %s, entry %d: %s%n  minimal entry: %s%n  minimal problem: %s%n  edited entry: %s"
                        .formatted(i, SEED, source.path(), index, problem, show(minimal), check(source, index, minimal), abbreviate(show(edited))));
            }
        }
    }

    @Test
    void wholeFilesOfTheWrongShapeDecodeWithoutThrowing() {
        // Not a list, or a list of odd things: nothing to keep, but nothing may escape either
        for (var kind : SOURCES_BY_KIND) {
            var codec = kind.getFirst().kind().codec();
            for (var odd : ODD_VALUES) {
                for (var root : List.of(lenient(odd), array(lenient(odd)), wrapValues(lenient(odd)))) {
                    var text = show(root);
                    assertDoesNotThrow(() -> codec.parse(JsonOps.INSTANCE, root), kind.getFirst().kind().name() + ": " + text);
                }
            }
        }
    }

    @Test
    void everyShippedFileIsCovered() {
        // A file the fuzz doesn't know how to read would be skipped silently
        int count = SOURCES_BY_KIND.stream().mapToInt(List::size).sum();
        assertTrue(count > 100, "only " + count + " files found");
        assertEquals(6, SOURCES_BY_KIND.size(), "a kind of file is missing");
    }

    @Test
    void decodingIsRepeatable() {
        // The isolation check compares entries by how they print, so that has to be stable
        for (var kind : SOURCES_BY_KIND)
            for (var source : kind)
                assertEquals(fingerprints(source, source.root()), fingerprints(source, source.root()), source.path());
    }

    private record Inner(@Nullable String value) {
    }

    private record Outer(List<Inner> list, Optional<Inner> optional) {
    }

    @Test
    void nullComponentsAreFoundAtAnyDepth() {
        assertNull(nullComponent(new Outer(List.of(new Inner("a")), Optional.of(new Inner("b"))), "entry"));
        assertEquals("entry.list[1].value", nullComponent(new Outer(List.of(new Inner("a"), new Inner(null)), Optional.empty()), "entry"));
        assertEquals("entry.optional.value", nullComponent(new Outer(List.of(), Optional.of(new Inner(null))), "entry"));
    }

    @Test
    void shrinkRemovesWhatDoesNotMatter() {
        var entry = JsonParser.parseString("{\"a\": 1, \"b\": [1, 2, {\"bad\": true}], \"c\": \"x\"}");
        var minimal = shrink(entry, e -> show(e).contains("bad"));
        assertEquals("{\"b\":[{\"bad\":true}]}", show(minimal));
    }

    // ---- The property ----------------------------------------------------------------------------------------

    /**
     * Decodes the file with entry {@code index} replaced by {@code edited}, and returns null if all is well,
     * otherwise a description of the problem
     */
    private static @Nullable String check(Source source, int index, JsonElement edited) {
        var root = source.root().deepCopy();
        var entries = source.kind().entries().apply(root);
        assert entries != null;
        entries.set(index, edited);

        Optional<?> decoded;
        try {
            decoded = source.kind().codec().parse(JsonOps.INSTANCE, root).resultOrPartial(ignored -> {
            });
        } catch (Throwable t) {
            return "decoding threw " + t;
        }

        // Whatever came through is whole
        if (decoded.isPresent()) {
            for (var entry : source.kind().decodedEntries().apply(decoded.get())) {
                var missing = nullComponent(entry, "entry");
                if (missing != null)
                    return missing + " decoded as null";
            }
        }

        // Every other entry as it was; the edited one gone, or there as whatever it now is
        var before = fingerprints(source, source.root());
        var after = decoded.map(d -> source.kind().decodedEntries().apply(d).stream().map(String::valueOf).toList()).orElse(List.of());
        var others = new ArrayList<>(before);
        others.remove(index);
        if (after.size() == before.size()) {
            var withoutEdited = new ArrayList<>(after);
            withoutEdited.remove(index);
            if (withoutEdited.equals(others))
                return null;
        } else if (after.equals(others)) {
            return null;
        }
        return "the other entries didn't decode as before: %d of %d entries came through".formatted(after.size(), before.size());
    }

    /**
     * The first component of a decoded record, at any depth (through records, lists and optionals), that is null: a
     * codec that turned bad input into null instead of an error
     */
    private static @Nullable String nullComponent(@Nullable Object value, String path) {
        if (value == null)
            return path;
        if (value instanceof Optional<?> optional)
            return optional.map(v -> nullComponent(v, path)).orElse(null);
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                var missing = nullComponent(list.get(i), path + "[" + i + "]");
                if (missing != null)
                    return missing;
            }
            return null;
        }
        if (value instanceof Record record) {
            for (var component : record.getClass().getRecordComponents()) {
                try {
                    var accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    var missing = nullComponent(accessor.invoke(record), path + "." + component.getName());
                    if (missing != null)
                        return missing;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return null;
    }

    private static List<String> fingerprints(Source source, JsonElement root) {
        var decoded = source.kind().codec().parse(JsonOps.INSTANCE, root).getOrThrow();
        return source.kind().decodedEntries().apply(decoded).stream().map(String::valueOf).toList();
    }

    // ---- Generator -------------------------------------------------------------------------------------------

    /**
     * An entry with a few random edits
     */
    private static JsonElement edit(Random random, JsonElement entry) {
        if (random.nextInt(40) == 0)
            return lenient(pick(random, ODD_VALUES));

        var result = entry.deepCopy();
        int edits = 1 + random.nextInt(random.nextInt(4) == 0 ? 6 : 2);
        for (int i = 0; i < edits; i++) {
            var nodes = new ArrayList<Node>();
            collect(result, null, null, -1, nodes);
            var node = pick(random, nodes);
            if (node.parent() == null)
                continue;
            var value = node.value();
            switch (random.nextInt(8)) {
                case 0, 1 -> node.replace(lenient(pick(random, ODD_VALUES)));
                case 2, 3 -> {
                    if (value instanceof JsonPrimitive p && p.isString())
                        node.replace(new JsonPrimitive(garble(random, p.getAsString())));
                    else
                        node.replace(lenient(pick(random, ODD_VALUES)));
                }
                case 4 -> node.remove();
                case 5 -> node.replace(array(value));
                case 6 -> {
                    if (value instanceof JsonObject o)
                        o.add(pick(random, new String[]{"unknown", "", "_comment", "id", "required", "Blocks"}), lenient(pick(random, ODD_VALUES)));
                    else if (value instanceof JsonArray a && !a.isEmpty())
                        a.add(a.get(random.nextInt(a.size())).deepCopy());
                    else
                        node.replace(deepNesting(random));
                }
                default -> node.replace(deepNesting(random));
            }
        }
        return result;
    }

    /**
     * A place in an entry: the value, and the member or element of its parent that holds it
     */
    private record Node(JsonElement value, @Nullable JsonElement parent, @Nullable String member, int element) {

        void replace(JsonElement with) {
            if (this.parent instanceof JsonObject o)
                o.add(this.member, with);
            else if (this.parent instanceof JsonArray a)
                a.set(this.element, with);
        }

        void remove() {
            if (this.parent instanceof JsonObject o)
                o.remove(this.member);
            else if (this.parent instanceof JsonArray a)
                a.remove(this.element);
        }
    }

    private static void collect(JsonElement value, @Nullable JsonElement parent, @Nullable String member, int element, List<Node> nodes) {
        nodes.add(new Node(value, parent, member, element));
        if (value instanceof JsonObject o) {
            for (var entry : o.entrySet())
                collect(entry.getValue(), o, entry.getKey(), -1, nodes);
        } else if (value instanceof JsonArray a) {
            for (int i = 0; i < a.size(); i++)
                collect(a.get(i), a, null, i, nodes);
        }
    }

    private static String garble(Random random, String text) {
        var sb = new StringBuilder(text);
        int edits = 1 + random.nextInt(3);
        for (int i = 0; i < edits; i++) {
            int at = sb.isEmpty() ? 0 : random.nextInt(sb.length() + 1);
            switch (random.nextInt(5)) {
                case 0, 1 -> sb.insert(at, pick(random, STRING_FRAGMENTS));
                case 2 -> sb.delete(at, Math.min(sb.length(), at + 1 + random.nextInt(4)));
                case 3 -> sb.setLength(at);
                default -> {
                    int end = Math.min(sb.length(), at + 1 + random.nextInt(10));
                    sb.insert(at, sb.substring(at, end));
                }
            }
        }
        return sb.toString();
    }

    private static JsonElement deepNesting(Random random) {
        int depth = random.nextBoolean() ? 1 + random.nextInt(20) : 500 + random.nextInt(1_500);
        JsonElement value = lenient(pick(random, ODD_VALUES));
        for (int i = 0; i < depth; i++)
            value = random.nextBoolean() ? array(value) : wrap("a", value);
        return value;
    }

    // ---- The fuzzing machinery -------------------------------------------------------------------------------

    /**
     * Greedy shrinking: removes members and elements anywhere in the entry, one at a time, for as long as it keeps
     * failing. The result is small, but not guaranteed to be the smallest possible.
     */
    private static JsonElement shrink(JsonElement input, Predicate<JsonElement> stillFails) {
        var current = input.deepCopy();
        boolean removed = true;
        while (removed) {
            removed = false;
            var nodes = new ArrayList<Node>();
            collect(current, null, null, -1, nodes);
            // Parents come before their children, so whole subtrees are tried first
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).parent() == null)
                    continue;
                var candidate = current.deepCopy();
                var candidateNodes = new ArrayList<Node>();
                collect(candidate, null, null, -1, candidateNodes);
                candidateNodes.get(i).remove();
                if (stillFails.test(candidate)) {
                    current = candidate;
                    removed = true;
                    break;
                }
            }
        }
        return current;
    }

    private static List<List<Source>> loadSources() {
        try {
            // Anchored on a file of the mod's own: other mods' assets folders are on the class path too
            var anchor = DsConfigFuzzTests.class.getResource("/assets/dsurround/lang/en_us.json");
            assertNotNull(anchor);
            var assets = Path.of(anchor.toURI()).getParent().getParent().getParent();
            var byKind = new LinkedHashMap<Kind, List<Source>>();
            try (Stream<Path> files = Files.walk(assets)) {
                for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    var relative = assets.relativize(file).toString().replace('\\', '/');
                    var parts = relative.split("/");
                    if (parts.length < 3 || !parts[1].equals("dsconfigs"))
                        continue;
                    // Not read with a codec: PresetLoaderTests covers it
                    if (parts[2].equals("presets.json"))
                        continue;
                    var kind = parts[2].equals("tags") ? TAGS : switch (parts[2]) {
                        case "biomes.json" -> BIOMES;
                        case "blocks.json" -> BLOCKS;
                        case "dimensions.json" -> DIMENSIONS;
                        case "sound_factories.json" -> SOUND_FACTORIES;
                        case "sound_mappings.json" -> SOUND_MAPPINGS;
                        default -> throw new IllegalStateException("Unknown kind of file " + relative);
                    };
                    var root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                    // Another mod's file naming its blocks only decodes with that mod installed, as in the game
                    if (kind.codec().parse(JsonOps.INSTANCE, root).isError())
                        continue;
                    byKind.computeIfAbsent(kind, k -> new ArrayList<>()).add(new Source(relative, kind, root));
                }
            }
            return List.copyOf(byKind.values());
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- Helpers ---------------------------------------------------------------------------------------------

    /**
     * Parses JSON text leniently, as the loader does: so NaN, Infinity and the like come through
     */
    private static JsonElement lenient(String json) {
        return JsonParser.parseString(json);
    }

    private static JsonArray array(JsonElement value) {
        var array = new JsonArray();
        array.add(value);
        return array;
    }

    private static JsonObject wrap(String member, JsonElement value) {
        var object = new JsonObject();
        object.add(member, value);
        return object;
    }

    private static JsonObject wrapValues(JsonElement value) {
        return wrap("values", array(value));
    }

    private static String abbreviate(String text) {
        return text.length() <= 1_000 ? text : text.substring(0, 1_000) + "... (" + text.length() + " characters)";
    }

    private static <T> T pick(Random random, T[] values) {
        return values[random.nextInt(values.length)];
    }

    private static <T> T pick(Random random, List<T> values) {
        return values.get(random.nextInt(values.size()));
    }

    /**
     * The JSON, written leniently so NaN and the like can be shown
     */
    private static String show(JsonElement element) {
        var out = new StringWriter();
        var writer = new JsonWriter(out);
        writer.setLenient(true);
        GSON.toJson(element, writer);
        return out.toString();
    }
}

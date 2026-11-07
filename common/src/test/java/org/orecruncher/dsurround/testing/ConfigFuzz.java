// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.testing;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.tags.TagFile;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.data.BiomeConfigRule;
import org.orecruncher.dsurround.config.data.BlockConfigRule;
import org.orecruncher.dsurround.config.data.DimensionConfigRule;
import org.orecruncher.dsurround.config.data.SoundMappingConfigRule;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Fuzzing the configuration files the mod ships (dsconfigs): reading them, by kind, and editing their entries the way
 * a hand-edited file might be broken, with shrinking to a minimal failing entry. Shared by {@code DsConfigFuzzTests}
 * (decoding) and {@code LibraryRulesFuzzTests} (what the libraries do with what decodes). The game must be
 * bootstrapped before the shipped files are read.
 */
public final class ConfigFuzz {

    private ConfigFuzz() {
    }

    /**
     * Values a hand-edited file might hold anywhere, as lenient JSON text, as the loader reads it
     */
    public static final String[] ODD_VALUES = {
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
    public static final String[] STRING_FRAGMENTS = {
            "[", "]", "=", ",", ":", "#", "@", "!", "(", ")", "'", "\"", "\\", ".", "..", "&&", "||", "+", "-",
            "*", "/", "%", "?", "<", ">", "==", "0", "9", "1e9", " ", "\t", "\n", "\u0000", "é", "\uD83D",
            "\uD83D\uDE00", "A", "true", "null", "minecraft", "c:", "#c:"
    };

    public static final Gson GSON = new Gson();

    /**
     * A kind of file: how it is decoded, and where its entries are
     */
    public record Kind(String name, Codec<?> codec, Function<Object, List<?>> decodedEntries,
                        Function<JsonElement, @Nullable JsonArray> entries) {
    }

    public static final Kind BIOMES = listKind("biomes", BiomeConfigRule.CODEC);
    public static final Kind BLOCKS = listKind("blocks", BlockConfigRule.CODEC);
    public static final Kind DIMENSIONS = listKind("dimensions", DimensionConfigRule.CODEC);
    public static final Kind SOUND_FACTORIES = listKind("sound_factories", SoundFactory.CODEC);
    public static final Kind SOUND_MAPPINGS = listKind("sound_mappings", SoundMappingConfigRule.CODEC);
    public static final Kind TAGS = new Kind("tags", TagFile.CODEC, tag -> ((TagFile) tag).entries(),
            root -> root instanceof JsonObject o && o.get("values") instanceof JsonArray a ? a : null);

    public static Kind listKind(String name, Codec<?> codec) {
        return new Kind(name, Codec.list(codec), list -> (List<?>) list, root -> root instanceof JsonArray a ? a : null);
    }

    /**
     * A file the mod ships
     */
    public record Source(String path, Kind kind, JsonElement root) {
    }

    public static List<String> fingerprints(Source source, JsonElement root) {
        var decoded = source.kind().codec().parse(JsonOps.INSTANCE, root).getOrThrow();
        return source.kind().decodedEntries().apply(decoded).stream().map(String::valueOf).toList();
    }

    // ---- Generator -------------------------------------------------------------------------------------------

    /**
     * An entry with a few random edits
     */
    public static JsonElement edit(Random random, JsonElement entry) {
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
    public record Node(JsonElement value, @Nullable JsonElement parent, @Nullable String member, int element) {

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

    public static void collect(JsonElement value, @Nullable JsonElement parent, @Nullable String member, int element, List<Node> nodes) {
        nodes.add(new Node(value, parent, member, element));
        if (value instanceof JsonObject o) {
            for (var entry : o.entrySet())
                collect(entry.getValue(), o, entry.getKey(), -1, nodes);
        } else if (value instanceof JsonArray a) {
            for (int i = 0; i < a.size(); i++)
                collect(a.get(i), a, null, i, nodes);
        }
    }

    public static String garble(Random random, String text) {
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

    public static JsonElement deepNesting(Random random) {
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
    public static JsonElement shrink(JsonElement input, Predicate<JsonElement> stillFails) {
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

    public static List<List<Source>> loadSources() {
        try {
            // Anchored on a file of the mod's own: other mods' assets folders are on the class path too
            var anchor = ConfigFuzz.class.getResource("/assets/dsurround/lang/en_us.json");
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
    public static JsonElement lenient(String json) {
        return JsonParser.parseString(json);
    }

    public static JsonArray array(JsonElement value) {
        var array = new JsonArray();
        array.add(value);
        return array;
    }

    public static JsonObject wrap(String member, JsonElement value) {
        var object = new JsonObject();
        object.add(member, value);
        return object;
    }

    public static JsonObject wrapValues(JsonElement value) {
        return wrap("values", array(value));
    }

    public static String abbreviate(String text) {
        return text.length() <= 1_000 ? text : text.substring(0, 1_000) + "... (" + text.length() + " characters)";
    }

    public static <T> T pick(Random random, T[] values) {
        return values[random.nextInt(values.length)];
    }

    public static <T> T pick(Random random, List<T> values) {
        return values.get(random.nextInt(values.size()));
    }

    /**
     * The JSON, written leniently so NaN and the like can be shown
     */
    public static String show(JsonElement element) {
        var out = new StringWriter();
        var writer = new JsonWriter(out);
        writer.setLenient(true);
        GSON.toJson(element, writer);
        return out.toString();
    }
}

package org.orecruncher.dsurround.lib.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.Library;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Feeds the loading of the mod's configuration file ({@code dsurround.json}) large numbers of broken and hand-edited
 * files, and checks what must hold for every one of them:
 * <ul>
 *     <li>Loading never throws: a bad file can't stop the game from starting.</li>
 *     <li>Every setting comes out usable: no missing group, no null, nothing out of its range, no NaN.</li>
 *     <li>A file that can't be read is kept, byte for byte, under a ".bad" name, and the defaults are used.</li>
 *     <li>Otherwise the file written back loads to the same settings, and loading it again changes nothing.</li>
 *     <li>A valid value survives whatever is wrong elsewhere in a file that can still be read.</li>
 * </ul>
 * Inputs come from two generators: edits to the file's structure (wrong types, odd numbers, missing or extra keys,
 * settings at the places they {@link MovedFrom moved from}), and edits to the text of the file the game writes
 * (stray characters, truncation, comments, a byte order mark).
 * <p>
 * Runs are reproducible. Case {@code i} always uses {@code new Random(seed + i)}. When a case fails, the input is
 * shrunk to a minimal one that still fails and is printed as a Java string literal, ready to paste into a regression
 * test in {@link ConfigurationDataTests}. To explore further, run with a different seed or many more cases:
 * {@code -Dconfig.fuzz.seed=123 -Dconfig.fuzz.cases=100000} (the build must pass system properties through to the
 * test JVM).
 */
class ConfigurationFuzzTests {

    static {
        // The configuration's defaults reach into the game's registries
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final int CASES = Integer.getInteger("config.fuzz.cases", 500);
    private static final long SEED = Long.getLong("config.fuzz.seed", 20261016L);

    private static final Gson GSON = new Gson();
    private static final String FILE_NAME = "dsurround.json";

    /**
     * Marks a string in a generated tree as JSON text to write as it is: lenient tokens Gson won't write itself
     */
    private static final String RAW = "\u0000raw:";

    /**
     * Values a hand-edited file might hold where a setting goes, as JSON text: wrong types, numbers too big or
     * awkward for their type, lenient tokens, and empty containers
     */
    private static final String[] ODD_VALUES = {
            "null", "true", "false", "0", "-1", "1", "7", "0.5", "-0.0", "1.0", "2.5e1",
            "2147483647", "2147483648", "-2147483649", "9223372036854775808", "99999999999999999999999",
            "1e308", "1e400", "-1e400", "1e-400", "4.9e-324", "NaN", "-NaN", "Infinity", "-Infinity",
            "0x10", "010", "+1", ".5", "5.",
            "\"\"", "\"5\"", "\"true\"", "\"NaN\"", "\"NONE\"", "\"none\"", "\" NONE \"", "'single'", "unquoted",
            "\"\\u0000\"", "\"\\ud83d\"", "\"\uD83D\uDE00\"", "\"a\\nb\"",
            "[]", "[1]", "[true, false]", "{}", "{\"a\": 1}", "{\"enabled\": false}"
    };

    /**
     * Pieces of text dropped into a file: JSON punctuation, comment starts, lenient tokens and awkward characters
     */
    private static final String[] FRAGMENTS = {
            "{", "}", "[", "]", ",", ",,", ":", "\"", "'", "\\", "\\u", "\\u12",
            "//", "/*", "*/", "#", "// note\n", "/* note */", "# note\n",
            "\n", "\r\n", "\r", "\t", " ", "\u00a0", "\u2028", "\u0000", "\uFEFF",
            "null", "true", "false", "NaN", "Infinity", "-", "1e999", "0", "-1", "2147483648",
            "\"x\"", "\"x\": 1", "\"x\": 1,", "é", "\uD83D", "\uDE00", "\uD83D\uDE00"
    };

    // Every value-holding setting, by its dotted path in the file
    private static final Map<String, ConfigElement.PropertyValue<?>> VALUES = new LinkedHashMap<>();
    // The same, by the kind of value they hold, so a kind with few settings (there is one double) is edited as
    // often as the others
    private static final List<List<String>> PATHS_BY_KIND = new ArrayList<>();
    // The groups, by their dotted path in the file, groups within groups included
    private static final List<String> GROUPS = new ArrayList<>();
    // The old places of settings that have moved, as @MovedFrom names them
    private static final List<String> OLD_PATHS = new ArrayList<>();
    // Settings a value at an old place can override, so a value put at their own place may not survive
    private static final Set<String> REPLACED_BY_OLD_PATHS = new HashSet<>();

    private static String defaultsText;
    private static JsonObject defaultsTree;
    private static Level savedLevel;

    @TempDir
    Path folder;

    @BeforeAll
    static void beforeAll() {
        // Every repaired value is logged, and every unreadable file with a stack trace: thousands of lines
        savedLevel = LogManager.getLogger(Library.MOD_ID).getLevel();
        Configurator.setLevel(Library.MOD_ID, Level.OFF);

        collect(ConfigurationData.getSpecification(Configuration.class), "");
        var byKind = new LinkedHashMap<Class<?>, List<String>>();
        VALUES.forEach((path, value) -> byKind.computeIfAbsent(value.getClass(), k -> new ArrayList<>()).add(path));
        PATHS_BY_KIND.addAll(byKind.values());
        defaultsText = text(ConfigProcessor.createPrototype(Configuration.class));
        defaultsTree = JsonParser.parseString(GSON.toJson(ConfigProcessor.createPrototype(Configuration.class))).getAsJsonObject();
    }

    @AfterAll
    static void afterAll() {
        Configurator.setLevel(Library.MOD_ID, savedLevel);
    }

    private static void collect(Collection<ConfigElement<?>> elements, String prefix) {
        for (var element : elements) {
            var path = prefix + element.fieldName();
            element.getAnnotation(MovedFrom.class).ifPresent(moved -> {
                OLD_PATHS.addAll(Arrays.asList(moved.value()));
                if (moved.replaces())
                    REPLACED_BY_OLD_PATHS.add(path);
            });
            if (element instanceof ConfigElement.PropertyGroup group) {
                GROUPS.add(path);
                collect(group.getChildren(), path + ".");
            } else if (element instanceof ConfigElement.PropertyValue<?> value) {
                VALUES.put(path, value);
            }
        }
    }

    // ---- The tests -------------------------------------------------------------------------------------------

    @Test
    void structurallyEditedFilesLoadSafely() {
        fuzz(CASES, ConfigurationFuzzTests::structuralCase);
    }

    @Test
    void textuallyEditedFilesLoadSafely() {
        fuzz(CASES, ConfigurationFuzzTests::textCase);
    }

    @Test
    void theDefaultsLoadAsTheyAreWritten() throws IOException {
        // The base the text edits start from must itself pass
        var problem = this.check(new Case(defaultsText, Map.of()));
        assertEquals(null, problem);
    }

    @Test
    void shrinkFindsTheSmallestFailingInput() {
        assertEquals("AB", shrink("xxAyyBzz-AB-qq", s -> s.contains("AB")));
        assertEquals("", shrink("anything", s -> true));
    }

    // ---- The properties --------------------------------------------------------------------------------------

    /**
     * A file to load, and the settings it holds valid values for that must come through: by dotted path, the value
     * as it should be loaded
     */
    private record Case(String input, Map<String, Object> kept) {
    }

    /**
     * Loads the case's file and returns null if everything that must hold does, otherwise a description of the
     * problem
     */
    private @Nullable String check(Case c) throws IOException {
        this.clearFolder();
        var file = this.folder.resolve(FILE_NAME);
        var bytes = c.input().getBytes(StandardCharsets.UTF_8);
        Files.write(file, bytes);

        Configuration config;
        try {
            config = ConfigurationData.load(Configuration.class, file);
        } catch (Throwable t) {
            return "load threw " + t;
        }

        var invalid = invalidValue(config);
        if (invalid != null)
            return invalid;

        var loaded = text(config);
        var backups = this.backups();
        if (backups.size() > 1)
            return backups.size() + " .bad files";

        if (backups.size() == 1) {
            // Unreadable: kept as it was, and the defaults used
            if (!Arrays.equals(bytes, Files.readAllBytes(backups.getFirst())))
                return "the .bad file isn't the original";
            if (!loaded.equals(defaultsText))
                return "an unreadable file didn't give the defaults";
        } else {
            for (var kept : c.kept().entrySet()) {
                Object actual = valueAt(config, kept.getKey());
                if (!Objects.equals(actual, kept.getValue()))
                    return "'%s' was %s in the file but loaded as %s".formatted(kept.getKey(), kept.getValue(), actual);
            }
        }

        if (!Files.exists(file))
            return "no file was written back";

        // What was written loads back to the same, and writing it again gives the same file
        var written = Files.readString(file);
        Configuration again;
        try {
            again = ConfigurationData.load(Configuration.class, file);
        } catch (Throwable t) {
            return "loading the written file threw " + t;
        }
        if (this.backups().size() != backups.size())
            return "the written file couldn't be read";
        if (!text(again).equals(loaded))
            return "the written file loads to different settings";
        if (!Files.readString(file).equals(written))
            return "loading the written file changed it";
        return null;
    }

    /**
     * The first setting that isn't usable: a missing group, a null, a value out of range or NaN
     */
    private static @Nullable String invalidValue(Configuration config) {
        return invalidValue(ConfigurationData.getSpecification(Configuration.class), config, "");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @Nullable String invalidValue(Collection<ConfigElement<?>> elements, Object instance, String prefix) {
        for (var element : elements) {
            var path = prefix + element.fieldName();
            if (element instanceof ConfigElement.PropertyGroup group) {
                var groupInstance = group.getInstance(instance);
                if (groupInstance == null)
                    return "group '" + path + "' is null";
                var problem = invalidValue(group.getChildren(), groupInstance, path + ".");
                if (problem != null)
                    return problem;
            } else if (element instanceof ConfigElement.PropertyValue value) {
                var current = value.getValue(instance);
                if (current == null)
                    return "'" + path + "' is null";
                // Clamping is what keeps NaN out, so it can't be the only check for it
                if (current instanceof Double d && !Double.isFinite(d))
                    return "'" + path + "' is " + d;
                if (!Objects.equals(value.clamp(current), current))
                    return "'" + path + "' is " + current + ", out of range";
            }
        }
        return null;
    }

    private static @Nullable Object valueAt(Configuration config, String path) {
        // Down through the groups the path names, to the one holding the setting
        Object group = config;
        var elements = ConfigurationData.getSpecification(Configuration.class);
        var parts = path.split("\\.");
        for (int i = 0; i < parts.length - 1; i++) {
            for (var element : elements) {
                if (element instanceof ConfigElement.PropertyGroup g && element.fieldName().equals(parts[i])) {
                    group = g.getInstance(group);
                    elements = g.getChildren();
                    break;
                }
            }
        }
        return VALUES.get(path).getValue(group);
    }

    // ---- Generators ------------------------------------------------------------------------------------------

    /**
     * The file the game writes, with its structure edited: settings given odd values or valid new ones, removed,
     * renamed, put at their old places; groups removed or replaced; unknown keys added. Valid new values that are
     * still in place at the end must come through.
     */
    private static Case structuralCase(Random random) {
        var tree = defaultsTree.deepCopy();
        var kept = new HashMap<String, Object>();

        // Rarely, not an object at all
        if (random.nextInt(50) == 0)
            return new Case(pick(random, ODD_VALUES), Map.of());

        int edits = 1 + random.nextInt(random.nextInt(5) == 0 ? 30 : 6);
        for (int i = 0; i < edits; i++) {
            var path = pick(random, pick(random, PATHS_BY_KIND));
            switch (random.nextInt(10)) {
                case 0, 1 -> MovedProperties.set(tree, path, raw(pick(random, ODD_VALUES)));
                case 2, 3 -> {
                    var value = validValue(random, VALUES.get(path));
                    MovedProperties.set(tree, path, GSON.toJsonTree(value));
                    kept.put(path, value);
                }
                case 4 -> remove(tree, path);
                case 5 -> {
                    // Same name in another case, which Gson doesn't match
                    var dot = path.lastIndexOf('.');
                    var renamed = path.substring(0, dot + 1) + flipCase(random, path.substring(dot + 1));
                    var value = MovedProperties.get(tree, path);
                    remove(tree, path);
                    if (value != null)
                        MovedProperties.set(tree, renamed, value);
                }
                case 6 -> {
                    var group = pick(random, GROUPS);
                    if (random.nextBoolean())
                        remove(tree, group);
                    else
                        MovedProperties.set(tree, group, raw(pick(random, ODD_VALUES)));
                }
                case 7 -> {
                    var where = random.nextBoolean() ? tree : objectAt(tree, pick(random, GROUPS));
                    if (where != null)
                        where.add(pick(random, new String[]{"unknown", "", " ", "enabled", "x.y", "\u00e9"}), raw(pick(random, ODD_VALUES)));
                }
                case 8 -> {
                    if (!OLD_PATHS.isEmpty()) {
                        var old = pick(random, OLD_PATHS);
                        var value = random.nextBoolean() ? new JsonPrimitive(random.nextBoolean()) : raw(pick(random, ODD_VALUES));
                        MovedProperties.set(tree, old, value);
                    }
                }
                default -> {
                    // A setting's group emptied
                    var group = objectAt(tree, pick(random, GROUPS));
                    if (group != null)
                        group.entrySet().clear();
                }
            }
        }

        // Only what is still in place, as written, at the end; and not where an old place can override it
        kept.entrySet().removeIf(e -> !Objects.equals(MovedProperties.get(tree, e.getKey()), GSON.toJsonTree(e.getValue()))
                || REPLACED_BY_OLD_PATHS.contains(e.getKey()));
        return new Case(write(tree, random), kept);
    }

    /**
     * The file the game writes, or a structurally edited one, with its text edited
     */
    private static Case textCase(Random random) {
        var text = random.nextInt(4) == 0 ? structuralCase(random).input() : defaultsText;
        var sb = new StringBuilder(text);
        int edits = 1 + random.nextInt(random.nextInt(5) == 0 ? 20 : 4);
        for (int i = 0; i < edits; i++) {
            int at = sb.isEmpty() ? 0 : random.nextInt(sb.length());
            switch (random.nextInt(8)) {
                case 0, 1 -> sb.insert(at, pick(random, FRAGMENTS));
                case 2 -> sb.delete(at, Math.min(sb.length(), at + 1 + random.nextInt(random.nextBoolean() ? 3 : 40)));
                case 3 -> {
                    int end = Math.min(sb.length(), at + 1 + random.nextInt(60));
                    sb.insert(at, sb.substring(at, end));
                }
                case 4 -> {
                    if (!sb.isEmpty())
                        sb.setCharAt(at, (char) (32 + random.nextInt(95)));
                }
                case 5 -> sb.setLength(at);
                case 6 -> sb.insert(at, deepNesting(random));
                default -> sb.insert(0, pick(random, new String[]{"\uFEFF", "// my settings\n", "\n\n", " "}));
            }
        }
        return new Case(sb.toString(), Map.of());
    }

    /**
     * A valid value for a setting: what it would be loaded as
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object validValue(Random random, ConfigElement.PropertyValue element) {
        var def = element.defaultValue();
        Object value = switch (def) {
            case Boolean ignored -> random.nextBoolean();
            case Integer ignored -> random.nextBoolean() ? random.nextInt(-5, 200) : random.nextInt();
            case Double ignored -> random.nextBoolean() ? random.nextDouble(-2D, 10D) : random.nextDouble() * Math.pow(10, random.nextInt(-5, 12));
            case String ignored -> pick(random, new String[]{"", "x", "a b", "\u00e9\uD83D\uDE00", "\"quoted\"", "line\nbreak"});
            case Enum<?> e -> {
                var constants = e.getDeclaringClass().getEnumConstants();
                yield constants[random.nextInt(constants.length)];
            }
            default -> def;
        };
        // In range, as the setting would hold it
        return element.clamp(value);
    }

    private static String deepNesting(Random random) {
        int depth = random.nextBoolean() ? 1 + random.nextInt(50) : 1_000 + random.nextInt(20_000);
        var open = random.nextBoolean() ? "[" : "{\"a\":";
        return open.repeat(depth);
    }

    // ---- The fuzzing machinery -------------------------------------------------------------------------------

    /**
     * Runs {@code cases} random cases. On the first failure the input is shrunk and the test fails with it.
     */
    private void fuzz(int cases, Function<Random, Case> generator) {
        for (int i = 0; i < cases; i++) {
            var random = new Random(SEED + i);
            var c = generator.apply(random);
            var problem = this.safely(c);
            if (problem != null) {
                // Shrink the text alone: the settings to keep would no longer match it
                var minimal = shrink(c.input(), candidate -> this.safely(new Case(candidate, Map.of())) != null);
                fail("case %d (seed %d): %s%n  minimal input: %s%n  minimal problem: %s%n  original input: %s"
                        .formatted(i, SEED, problem, show(minimal), this.safely(new Case(minimal, Map.of())), show(c.input())));
            }
        }
    }

    private @Nullable String safely(Case c) {
        try {
            return this.check(c);
        } catch (Throwable t) {
            return "threw " + t;
        }
    }

    /**
     * Greedy shrinking: repeatedly deletes chunks (large first, down to single characters) as long as the input keeps
     * failing. The result is small, but not guaranteed to be the smallest possible.
     */
    private static String shrink(String input, Predicate<String> stillFails) {
        String current = input;
        int chunk = Math.max(1, current.length() / 2);
        while (true) {
            boolean removed = false;
            for (int start = 0; start + chunk <= current.length(); ) {
                String candidate = current.substring(0, start) + current.substring(start + chunk);
                if (stillFails.test(candidate)) {
                    current = candidate;
                    removed = true;
                } else {
                    start += chunk;
                }
            }
            if (!removed) {
                if (chunk == 1)
                    return current;
                chunk = Math.max(1, chunk / 2);
            }
        }
    }

    private void clearFolder() throws IOException {
        try (var files = Files.list(this.folder)) {
            for (var file : files.toList())
                Files.delete(file);
        }
    }

    private List<Path> backups() throws IOException {
        try (var files = Files.list(this.folder)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".bad")).toList();
        }
    }

    // ---- Generators and helpers ------------------------------------------------------------------------------

    private static String text(Object config) {
        return CommentedJson.write(config);
    }

    private static JsonPrimitive raw(String json) {
        return new JsonPrimitive(RAW + json);
    }

    private static void remove(JsonObject tree, String path) {
        var dot = path.lastIndexOf('.');
        var parent = dot < 0 ? tree : objectAt(tree, path.substring(0, dot));
        if (parent != null)
            parent.remove(path.substring(dot + 1));
    }

    private static @Nullable JsonObject objectAt(JsonObject tree, String path) {
        return MovedProperties.get(tree, path) instanceof JsonObject object ? object : null;
    }

    private static String flipCase(Random random, String name) {
        int i = random.nextInt(name.length());
        char c = name.charAt(i);
        char flipped = Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c);
        return name.substring(0, i) + flipped + name.substring(i + 1);
    }

    private static <T> T pick(Random random, T[] values) {
        return values[random.nextInt(values.length)];
    }

    private static <T> T pick(Random random, List<T> values) {
        return values.get(random.nextInt(values.size()));
    }

    /**
     * Writes a generated tree as JSON, with {@link #raw} values as they are, and now and then a comment or a layout a
     * person might use
     */
    private static String write(JsonElement element, Random random) {
        var sb = new StringBuilder();
        write(element, random, sb, 0);
        return sb.toString();
    }

    private static void write(JsonElement element, Random random, StringBuilder sb, int depth) {
        if (element instanceof JsonObject object) {
            sb.append('{');
            boolean first = true;
            for (var entry : object.entrySet()) {
                if (!first)
                    sb.append(',');
                first = false;
                sb.append('\n').append("  ".repeat(depth + 1));
                if (random.nextInt(40) == 0)
                    sb.append(pick(random, new String[]{"// note\n", "/* note */ ", "# note\n"})).append("  ".repeat(depth + 1));
                sb.append(GSON.toJson(entry.getKey())).append(": ");
                write(entry.getValue(), random, sb, depth + 1);
            }
            sb.append('\n').append("  ".repeat(depth)).append('}');
        } else if (element instanceof JsonArray array) {
            sb.append('[');
            for (int i = 0; i < array.size(); i++) {
                if (i > 0)
                    sb.append(", ");
                write(array.get(i), random, sb, depth + 1);
            }
            sb.append(']');
        } else if (element instanceof JsonPrimitive primitive && primitive.isString() && primitive.getAsString().startsWith(RAW)) {
            sb.append(primitive.getAsString().substring(RAW.length()));
        } else {
            sb.append(GSON.toJson(element));
        }
    }

    /**
     * Formats text as a Java string literal, so a failing input can be pasted straight into a test.
     */
    private static String show(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 32 || c > 126)
                        sb.append(String.format("\\u%04x", (int) c));
                    else
                        sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}

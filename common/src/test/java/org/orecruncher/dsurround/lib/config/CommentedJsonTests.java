package org.orecruncher.dsurround.lib.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orecruncher.dsurround.lib.config.ConfigurationData.*;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for reading and writing a configuration as JSON with each property's comment, range and default above it.
 */
public class CommentedJsonTests {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // What the writer should match, comments aside
    private static final Gson PLAIN = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @TempDir
    Path folder;

    // ---- Fixtures --------------------------------------------------------------------------------------------

    @Comment(value = "Options for the group", inherit = true)
    public static class Group {
        @Property
        @Comment("How loud it is")
        @IntegerRange(min = 1, max = 3)
        public int level = 2;

        @Property
        public boolean on = true;
    }

    @Comment("Only for the config screen")
    public static class NotInherited {
        @Property
        public int x = 1;
    }

    public enum Mode {ALPHA, BETA}

    public enum Named {
        @SerializedName("first") ONE,
        TWO
    }

    @ConfigPlacement(folderName = "test", fileName = "commented")
    public static class CommentedConfig extends ConfigurationData {
        @Property
        @Comment("Turns the feature on")
        public boolean flag = true;

        @Property
        @Comment("First line\nSecond line")
        @IntegerRange(min = 0, max = 10)
        public int count = 5;

        @Property
        @IntegerRange(min = 0)
        public int atLeast = 3;

        @Property
        @Slider(min = 2, max = 8)
        public int slid = 4;

        @Property
        @DoubleSlider(min = 0D, max = 1D, step = 0.05D)
        public double fraction = 0.25D;

        @Property
        @DoubleRange(min = 0.5D, max = 4D)
        public double scale = 1.5D;

        @Property
        public Mode mode = Mode.BETA;

        @Property
        public Named named = Named.TWO;

        // No comment of its own: the type's is inherited
        @Property
        public final Group group = new Group();

        @Property
        @Comment("Overrides the type's comment")
        public final Group other = new Group();

        // No comment of its own, and the type's isn't marked to be inherited
        @Property
        public final NotInherited plain = new NotInherited();

        @Property
        @Comment("  ")
        public String blank = "a // not a comment, < & >";

        @Property
        public List<String> names = new ArrayList<>(List.of("x", "y"));

        @Property
        public List<String> empty = new ArrayList<>();

        @Property
        public Map<String, Integer> map = new LinkedHashMap<>(Map.of("k", 1));

        public String nothing = null;
    }

    // Records have no no-argument constructor, so there are no defaults to show
    public record NoDefaults(int a, String b) {
    }

    private static String write(Object value) {
        return CommentedJson.write(value);
    }

    private static List<String> lines(String json) {
        return json.lines().map(String::strip).toList();
    }

    /**
     * The lines just above {@code key}'s line, top to bottom.
     */
    private static List<String> commentsAbove(List<String> lines, String keyLine) {
        int at = lines.indexOf(keyLine);
        assertTrue(at >= 0, "no line " + keyLine + " in " + lines);
        int first = at;
        while (first > 0 && lines.get(first - 1).startsWith("//"))
            first--;
        return lines.subList(first, at);
    }

    // ---- Comments -----------------------------------------------------------------------------------------------

    @Test
    void commentThenDefault() {
        var lines = lines(write(new CommentedConfig()));

        assertEquals(List.of("// Turns the feature on", "// Default: true"), commentsAbove(lines, "\"flag\": true,"));
    }

    @Test
    void multilineCommentsGetALineEachThenTheRange() {
        var lines = lines(write(new CommentedConfig()));

        assertEquals(List.of("// First line", "// Second line", "// Range: 0 - 10, default: 5"),
                commentsAbove(lines, "\"count\": 5,"));
    }

    @Test
    void rangeWithoutAMaximumIsAMinimum() {
        var lines = lines(write(new CommentedConfig()));

        assertEquals(List.of("// Minimum: 0, default: 3"), commentsAbove(lines, "\"atLeast\": 3,"));
    }

    @Test
    void sliderRangeIsShown() {
        var lines = lines(write(new CommentedConfig()));

        assertEquals(List.of("// Range: 2 - 8, default: 4"), commentsAbove(lines, "\"slid\": 4,"));
    }

    @Test
    void doubleSliderRangeAndStepAreShown() {
        var lines = lines(write(new CommentedConfig()));

        assertEquals(List.of("// Range: 0 - 1, step: 0.05, default: 0.25"), commentsAbove(lines, "\"fraction\": 0.25,"));
    }

    @Test
    void doubleRangesDropTrailingZeros() {
        var lines = lines(write(new CommentedConfig()));

        assertEquals(List.of("// Range: 0.5 - 4, default: 1.5"), commentsAbove(lines, "\"scale\": 1.5,"));
    }

    @Test
    void enumsListTheirValues() {
        var lines = lines(write(new CommentedConfig()));

        assertEquals(List.of("// Values: ALPHA, BETA, default: \"BETA\""), commentsAbove(lines, "\"mode\": \"BETA\","));
    }

    @Test
    void enumValuesAreShownAsGsonWritesThem() {
        // A constant Gson writes under another name is listed by that name, so it can be copied into the file
        var lines = lines(write(new CommentedConfig()));

        assertEquals(List.of("// Values: first, TWO, default: \"TWO\""), commentsAbove(lines, "\"named\": \"TWO\","));
    }

    @Test
    void theDefaultIsShownNotTheCurrentValue() {
        var config = new CommentedConfig();
        config.count = 9;

        var lines = lines(write(config));

        assertEquals("// Range: 0 - 10, default: 5", commentsAbove(lines, "\"count\": 9,").getLast());
    }

    @Test
    void anInheritableTypeCommentIsUsed() {
        var lines = lines(write(new CommentedConfig()));

        assertEquals(List.of("// Options for the group"), commentsAbove(lines, "\"group\": {"));
    }

    @Test
    void aPropertysOwnCommentWins() {
        var lines = lines(write(new CommentedConfig()));

        assertEquals(List.of("// Overrides the type's comment"), commentsAbove(lines, "\"other\": {"));
    }

    @Test
    void typeCommentsAreNotInheritedUnlessMarked() {
        var lines = lines(write(new CommentedConfig()));

        assertEquals(List.of(), commentsAbove(lines, "\"plain\": {"));
        assertFalse(lines.contains("// Only for the config screen"));
    }

    @Test
    void nestedPropertiesHaveTheirCommentsAndDefaultsIndented() {
        var json = write(new CommentedConfig());

        assertTrue(json.contains("\n    // How loud it is\n    // Range: 1 - 3, default: 2\n    \"level\": 2,"), json);
        assertTrue(json.contains("\n    // Default: true\n    \"on\": true\n"), json);
    }

    @Test
    void blankCommentsAreLeftOut() {
        var lines = lines(write(new CommentedConfig()));

        assertEquals(List.of("// Default: \"a // not a comment, < & >\""),
                commentsAbove(lines, "\"blank\": \"a // not a comment, < & >\","));
    }

    @Test
    void listsAndMapsHaveNoDefaults() {
        var lines = lines(write(new CommentedConfig()));

        assertEquals(List.of(), commentsAbove(lines, "\"names\": ["));
        assertEquals(List.of(), commentsAbove(lines, "\"map\": {"));
    }

    // ---- Same JSON as Gson --------------------------------------------------------------------------------------

    @Test
    void withNothingToCommentTheOutputIsGsons() {
        var value = new NoDefaults(1, "two");

        assertEquals(PLAIN.toJson(value), write(value));
    }

    @Test
    void layoutMatchesGsonOnceCommentsAreRemoved() {
        // Arrays, empty arrays, maps, nested objects and numbers are laid out exactly as Gson would
        var value = new CommentedConfig();
        var withoutComments = write(value).lines()
                .filter(l -> !l.strip().startsWith("//"))
                .reduce((a, b) -> a + "\n" + b)
                .orElseThrow();

        assertEquals(PLAIN.toJson(value), withoutComments);
    }

    @Test
    void readsBackAsTheSameJson() {
        var value = new CommentedConfig();

        assertEquals(GSON.toJsonTree(value), JsonParser.parseString(write(value)));
    }

    @Test
    void transientAndNullFieldsAreLeftOut() {
        var json = write(new CommentedConfig());

        assertFalse(json.contains("configFilePath"), json);
        assertFalse(json.contains("nothing"), json);
    }

    // ---- Reading ------------------------------------------------------------------------------------------------

    @Test
    void readsWhatItWrites() {
        var config = new CommentedConfig();
        config.count = 9;
        config.scale = 2.5D;
        config.mode = Mode.ALPHA;
        config.group.level = 3;
        config.names.add("z");

        var read = CommentedJson.read(new StringReader(write(config)), CommentedConfig.class);

        assertNotNull(read);
        assertEquals(GSON.toJsonTree(config), GSON.toJsonTree(read));
    }

    @Test
    void readsEveryCommentStyle() {
        // Lenient parsing is set explicitly, so this doesn't depend on Gson's defaults
        var read = CommentedJson.read(new StringReader("""
                // line comment
                {
                  /* block
                     comment */
                  "count": 7,
                  # hash comment
                  "flag": false // trailing comment
                }
                """), CommentedConfig.class);

        assertNotNull(read);
        assertEquals(7, read.count);
        assertFalse(read.flag);
    }

    @Test
    void emptyInputGivesNull() {
        assertNull(CommentedJson.read(new StringReader(""), CommentedConfig.class));
        assertNull(CommentedJson.read(new StringReader("  // only a comment\n"), CommentedConfig.class));
    }

    @Test
    void contentAfterTheObjectIsAnError() {
        // As Gson.fromJson(Reader) does: a second object, or a stray value, means the file isn't what was written
        assertThrows(JsonSyntaxException.class,
                () -> CommentedJson.read(new StringReader("{\"count\": 1} {\"count\": 2}"), CommentedConfig.class));
        assertThrows(JsonSyntaxException.class,
                () -> CommentedJson.read(new StringReader("{\"count\": 1} 42"), CommentedConfig.class));
    }

    @Test
    void trailingCommentsAfterTheObjectAreFine() {
        var read = CommentedJson.read(new StringReader("{\"count\": 1}\n// the end\n"), CommentedConfig.class);

        assertNotNull(read);
        assertEquals(1, read.count);
    }

    @Test
    void malformedJsonIsAnError() {
        assertThrows(JsonParseException.class,
                () -> CommentedJson.read(new StringReader("{\"count\": 7,, \"name\": "), CommentedConfig.class));
    }

    @Test
    void wrongTypesAreAnError() {
        assertThrows(JsonSyntaxException.class,
                () -> CommentedJson.read(new StringReader("{\"count\": \"lots\"}"), CommentedConfig.class));
    }

    // ---- Through the configuration ------------------------------------------------------------------------------

    @Test
    void savedFileHasCommentsAndLoadsBack() throws IOException {
        var path = this.folder.resolve("commented.json");
        var config = ConfigurationData.load(CommentedConfig.class, path);
        config.count = 9;
        config.group.level = 3;
        config.save();

        var text = Files.readString(path);
        assertTrue(text.contains("// Turns the feature on"), text);
        assertTrue(text.contains("// Range: 0 - 10, default: 5"), text);

        var reloaded = ConfigurationData.load(CommentedConfig.class, path);
        assertEquals(9, reloaded.count);
        assertEquals(3, reloaded.group.level);
    }

    @Test
    void aFileWithExtraContentIsKeptAsUnreadable() throws IOException {
        // Same as before the reader moved here: anything after the object makes the file unreadable, so it is
        // renamed rather than overwritten
        var path = this.folder.resolve("commented.json");
        Files.writeString(path, "{\"count\": 7} {\"count\": 8}");

        var config = ConfigurationData.load(CommentedConfig.class, path);

        assertEquals(5, config.count, "the defaults are used");
        try (var files = Files.list(this.folder)) {
            assertEquals(1, files.filter(p -> p.getFileName().toString().endsWith(".bad")).count());
        }
    }

    @Test
    void handWrittenCommentsAreReadThenReplaced() throws IOException {
        // Gson reads leniently, so a file with comments of any style loads; saving writes the annotations' comments
        var path = this.folder.resolve("commented.json");
        Files.writeString(path, """
                {
                  // my note
                  "count": 7, /* inline */
                  # hash style
                  "flag": false
                }
                """);

        var config = ConfigurationData.load(CommentedConfig.class, path);

        assertEquals(7, config.count);
        assertFalse(config.flag);
        var text = Files.readString(path);
        assertFalse(text.contains("my note"), text);
        assertTrue(text.contains("// Turns the feature on"), text);
    }
}

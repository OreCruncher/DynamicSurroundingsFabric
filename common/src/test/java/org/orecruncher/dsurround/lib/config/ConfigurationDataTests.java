package org.orecruncher.dsurround.lib.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.eventing.IConfigChangedEvent;
import org.orecruncher.dsurround.lib.config.ConfigurationData.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for building a configuration's specification, and loading, repairing and saving it. Files are written to
 * a temporary folder, not the game's config folder.
 */
public class ConfigurationDataTests {

    @TempDir
    Path folder;

    // ---- Fixtures --------------------------------------------------------------------------------------------

    public enum Mode {ALPHA, BETA}

    public static class Group {
        @Property
        @IntegerRange(min = 1, max = 3)
        public int level = 2;

        @Property
        public boolean on = true;
    }

    @ConfigPlacement(folderName = "test", fileName = "test")
    public static class TestConfig extends ConfigurationData {
        @Property
        public final Group group = new Group();

        @Property
        public boolean flag = true;

        @Property
        @IntegerRange(min = 0, max = 10)
        public int count = 5;

        @Property
        public Integer boxedCount = 3;

        @Property
        public String name = "default";

        @Property
        public Mode mode = Mode.ALPHA;

        @Property
        @DoubleRange(min = 0.5D, max = 4D)
        public double scale = 1D;

        @Property
        public double unbounded = -2.5D;

        @Property
        @IntegerRange(min = 1)
        public int atLeastOne = 1;

        @Property
        @DoubleRange(min = 0D)
        public double atLeastZero = 0D;

        @Property
        @Slider(min = 0, max = 10)
        public int slider = 5;

        @Property
        @DoubleSlider(min = 0.5D, max = 4D, step = 0.25D)
        public double doubleSlider = 1.5D;

        @Property
        public float unsupportedFloat = 1F;

        @Property
        @RestartRequired(client = false)
        public boolean needsWorldRestart = true;

        @Property
        @RestartRequired
        public boolean needsClientRestart = true;

        @Property
        @TextStyle(color = "#FF0000", italic = true)
        public boolean styled = true;

        @Property
        @TextStyle(color = "not a color", bold = true)
        public boolean badlyStyled = true;

        public int notAProperty = 7;
    }

    public static class NullDefaultConfig extends ConfigurationData {
        @Property
        public String name = null;
    }

    public static class NaNDefaultConfig extends ConfigurationData {
        @Property
        public double value = Double.NaN;
    }

    public static class SliderAndRangeConfig extends ConfigurationData {
        @Property
        @IntegerRange(min = 0, max = 10)
        @Slider(min = 0, max = 10)
        public int value = 5;
    }

    public static class SliderOnDoubleConfig extends ConfigurationData {
        @Property
        @Slider(min = 0, max = 10)
        public double value = 5D;
    }

    public static class InvertedSliderConfig extends ConfigurationData {
        @Property
        @Slider(min = 10, max = 0)
        public int value = 5;
    }

    public static class DoubleSliderAndRangeConfig extends ConfigurationData {
        @Property
        @DoubleRange(min = 0D, max = 1D)
        @DoubleSlider(min = 0D, max = 1D, step = 0.1D)
        public double value = 0.5D;
    }

    public static class DoubleSliderOnIntConfig extends ConfigurationData {
        @Property
        @DoubleSlider(min = 0D, max = 10D, step = 1D)
        public int value = 5;
    }

    public static class UnevenDoubleSliderConfig extends ConfigurationData {
        @Property
        @DoubleSlider(min = 0D, max = 1D, step = 0.3D)
        public double value = 0.3D;
    }

    public static class OffGridDefaultConfig extends ConfigurationData {
        @Property
        @DoubleSlider(min = 0D, max = 1D, step = 0.1D)
        public double value = 0.35D;
    }

    private Path configFile() {
        return this.folder.resolve("test.json");
    }

    private void writeFile(String json) throws IOException {
        Files.writeString(this.configFile(), json);
    }

    private JsonObject readFile() throws IOException {
        return JsonParser.parseString(Files.readString(this.configFile())).getAsJsonObject();
    }

    private static ConfigElement<?> element(Collection<ConfigElement<?>> elements, String fieldName) {
        return elements.stream()
                .filter(e -> e.getLanguageKey().endsWith("." + fieldName))
                .findFirst()
                .orElse(null);
    }

    private static Collection<ConfigElement<?>> spec() {
        return ConfigurationData.getSpecification(TestConfig.class);
    }

    // ---- Specification ---------------------------------------------------------------------------------------

    @Test
    void eachSupportedTypeGetsTheRightElement() {
        var spec = spec();

        assertInstanceOf(ConfigElement.BooleanValue.class, element(spec, "flag"));
        assertInstanceOf(ConfigElement.IntegerValue.class, element(spec, "count"));
        assertInstanceOf(ConfigElement.StringValue.class, element(spec, "name"));
        assertInstanceOf(ConfigElement.DoubleValue.class, element(spec, "scale"));
        assertInstanceOf(ConfigElement.PropertyGroup.class, element(spec, "group"));
    }

    @Test
    void boxedTypeIsAValueNotAGroup() {
        // Regression: non-primitive types other than enums all became (empty) property groups
        assertInstanceOf(ConfigElement.IntegerValue.class, element(spec(), "boxedCount"));
    }

    @Test
    void enumTypeComesFromTheFieldWithoutAnnotation() {
        var mode = assertInstanceOf(ConfigElement.EnumValue.class, element(spec(), "mode"));

        assertEquals(Mode.class, mode.getEnumClass());
    }

    @Test
    void unsupportedTypesAndUnmarkedFieldsAreLeftOut() {
        // Regression: a float got a DoubleValue, which failed reading (Float isn't Double) and writing
        assertNull(element(spec(), "unsupportedFloat"));
        assertNull(element(spec(), "notAProperty"));
    }

    @Test
    void groupHoldsItsProperties() {
        var group = assertInstanceOf(ConfigElement.PropertyGroup.class, element(spec(), "group"));

        assertEquals(Group.class, group.getType());
        assertEquals(2, group.getChildren().size());
        assertEquals(Constants.MOD_ID + ".group.level", element(group.getChildren(), "level").getLanguageKey());
    }

    @Test
    void languageKeysUseTheTranslationRoot() {
        // TestConfig has no @TranslationRoot, so the mod id is used
        assertEquals(Constants.MOD_ID + ".flag", element(spec(), "flag").getLanguageKey());
        assertEquals(Constants.MOD_ID + ".flag.tooltip", element(spec(), "flag").getTooltipLanguageKey());
    }

    @Test
    void defaultsComeFromAFreshInstance() {
        var count = (ConfigElement.IntegerValue) element(spec(), "count");
        var name = (ConfigElement.StringValue) element(spec(), "name");

        assertEquals(5, count.defaultValue());
        assertEquals("default", name.defaultValue());
    }

    @Test
    void rangesAreRecorded() {
        var count = (ConfigElement.IntegerValue) element(spec(), "count");
        var scale = (ConfigElement.DoubleValue) element(spec(), "scale");

        assertTrue(count.hasRange());
        assertEquals(0, count.getMinValue());
        assertEquals(10, count.getMaxValue());
        assertTrue(scale.hasRange());
        assertEquals(0.5D, scale.getMinValue());
    }

    @Test
    void doubleWithoutRangeAllowsNegativeValues() {
        // Regression: the default minimum was Double.MIN_VALUE, the smallest positive double
        var unbounded = (ConfigElement.DoubleValue) element(spec(), "unbounded");
        var config = new TestConfig();

        assertFalse(unbounded.hasRange());
        unbounded.setValue(config, -100D);
        assertEquals(-100D, config.unbounded);
    }

    @Test
    void nullDefaultIsRejected() {
        // A missing value is replaced with the default, so there has to be one
        var e = assertThrows(IllegalStateException.class, () -> ConfigurationData.getSpecification(NullDefaultConfig.class));
        assertTrue(e.getMessage().contains("'name'"), e.getMessage());
    }

    @Test
    void nanDefaultIsRejected() {
        var e = assertThrows(IllegalStateException.class, () -> ConfigurationData.getSpecification(NaNDefaultConfig.class));
        assertTrue(e.getMessage().contains("'value'"), e.getMessage());
    }

    private static void assertTooltip(Component tooltip, String key, Object... args) {
        var contents = assertInstanceOf(TranslatableContents.class, tooltip.getContents());
        assertEquals(key, contents.getKey());
        assertArrayEquals(args, contents.getArgs());
    }

    @Test
    void rangeTooltips() {
        // Regression: a range with no maximum showed Integer.MAX_VALUE or Double.MAX_VALUE as its upper limit
        var spec = spec();

        assertTooltip(((ConfigElement.IntegerValue) element(spec, "count")).getRangeTooltip(), "dsurround.config.tooltip.range", 0, 10);
        assertTooltip(((ConfigElement.IntegerValue) element(spec, "atLeastOne")).getRangeTooltip(), "dsurround.config.tooltip.minimum", 1);
        assertTooltip(((ConfigElement.DoubleValue) element(spec, "scale")).getRangeTooltip(), "dsurround.config.tooltip.range", "0.5", "4");
        assertTooltip(((ConfigElement.DoubleValue) element(spec, "atLeastZero")).getRangeTooltip(), "dsurround.config.tooltip.minimum", "0");
    }

    @Test
    void sliderHoldsTheRange() {
        var slider = (ConfigElement.IntegerValue) element(spec(), "slider");

        assertTrue(slider.useSlider());
        assertEquals(0, slider.getMinValue());
        assertEquals(10, slider.getMaxValue());
        assertFalse(((ConfigElement.IntegerValue) element(spec(), "count")).useSlider(), "no @Slider");
    }

    @Test
    void sliderAndIntegerRangeTogetherAreRejected() {
        var e = assertThrows(IllegalStateException.class, () -> ConfigurationData.getSpecification(SliderAndRangeConfig.class));
        assertTrue(e.getMessage().contains("'value'"), e.getMessage());
    }

    @Test
    void sliderOnANonIntIsRejected() {
        var e = assertThrows(IllegalStateException.class, () -> ConfigurationData.getSpecification(SliderOnDoubleConfig.class));
        assertTrue(e.getMessage().contains("'value'"), e.getMessage());
    }

    @Test
    void sliderMinimumMustBeBelowItsMaximum() {
        var e = assertThrows(IllegalStateException.class, () -> ConfigurationData.getSpecification(InvertedSliderConfig.class));
        assertTrue(e.getMessage().contains("'value'"), e.getMessage());
    }

    @Test
    void doubleSliderHoldsTheRange() {
        var slider = (ConfigElement.DoubleValue) element(spec(), "doubleSlider");

        assertTrue(slider.useSlider());
        assertEquals(0.5D, slider.getMinValue());
        assertEquals(4D, slider.getMaxValue());
        assertNotNull(slider.getSliderScale());
        assertEquals(14, slider.getSliderScale().lastIndex());
        assertFalse(((ConfigElement.DoubleValue) element(spec(), "scale")).useSlider(), "no @DoubleSlider");
    }

    @Test
    void doubleSliderAndDoubleRangeTogetherAreRejected() {
        var e = assertThrows(IllegalStateException.class, () -> ConfigurationData.getSpecification(DoubleSliderAndRangeConfig.class));
        assertTrue(e.getMessage().contains("'value'"), e.getMessage());
    }

    @Test
    void doubleSliderOnANonDoubleIsRejected() {
        var e = assertThrows(IllegalStateException.class, () -> ConfigurationData.getSpecification(DoubleSliderOnIntConfig.class));
        assertTrue(e.getMessage().contains("'value'"), e.getMessage());
    }

    @Test
    void invalidDoubleSliderIsRejected() {
        var e = assertThrows(IllegalStateException.class, () -> ConfigurationData.getSpecification(UnevenDoubleSliderConfig.class));
        assertTrue(e.getMessage().contains("'value'") && e.getMessage().contains("evenly"), e.getMessage());
    }

    @Test
    void doubleSliderDefaultMustBeAPosition() {
        // Otherwise the slider's reset button would set a value near the default rather than the default
        var e = assertThrows(IllegalStateException.class, () -> ConfigurationData.getSpecification(OffGridDefaultConfig.class));
        assertTrue(e.getMessage().contains("'value'"), e.getMessage());
    }

    @Test
    void restartKinds() {
        var world = (ConfigElement.PropertyValue<?>) element(spec(), "needsWorldRestart");
        var client = (ConfigElement.PropertyValue<?>) element(spec(), "needsClientRestart");

        assertTrue(world.isWorldRestartRequired());
        assertFalse(world.isClientRestartRequired(), "a world restart does not need Minecraft restarted");
        assertTrue(client.isClientRestartRequired());
        assertFalse(client.isWorldRestartRequired());
    }

    @Test
    void textStyleIsParsed() {
        var styled = element(spec(), "styled").getTextStyle();
        var badlyStyled = element(spec(), "badlyStyled").getTextStyle();

        assertNotNull(styled.getColor());
        assertEquals(0xFF0000, styled.getColor().getValue());
        assertTrue(styled.isItalic());

        // An invalid colour is left out; the rest of the style still applies
        assertNull(badlyStyled.getColor());
        assertTrue(badlyStyled.isBold());
    }

    // ---- Values ----------------------------------------------------------------------------------------------

    @Test
    void binderClampsToTheRange() {
        var count = (ConfigElement.IntegerValue) element(spec(), "count");
        var config = new TestConfig();
        var binder = count.<Integer>createBinder(config);

        binder.setValue(99);

        assertEquals(10, config.count);
        assertEquals(10, binder.getValue());
        assertEquals(5, binder.defaultValue());
    }

    @Test
    void accessorFailureNamesTheField() {
        // Regression: errors were logged and turned into null
        var count = (ConfigElement.IntegerValue) element(spec(), "count");

        var e = assertThrows(IllegalStateException.class, () -> count.getValue("not a config"));
        assertTrue(e.getMessage().contains("'count'"), e.getMessage());
    }

    // ---- Loading ---------------------------------------------------------------------------------------------

    @Test
    void missingFileIsCreatedWithDefaults() throws IOException {
        var config = ConfigurationData.load(TestConfig.class, this.configFile());

        assertEquals(5, config.count);
        assertTrue(Files.exists(this.configFile()));
        assertEquals(5, this.readFile().get("count").getAsInt());
        assertEquals(2, this.readFile().getAsJsonObject("group").get("level").getAsInt());
    }

    @Test
    void valuesAreReadFromTheFile() throws IOException {
        this.writeFile("""
                {"count": 7, "name": "custom", "mode": "BETA", "scale": 2.5, "group": {"level": 1, "on": false}}
                """);

        var config = ConfigurationData.load(TestConfig.class, this.configFile());

        assertEquals(7, config.count);
        assertEquals("custom", config.name);
        assertEquals(Mode.BETA, config.mode);
        assertEquals(2.5D, config.scale);
        assertEquals(1, config.group.level);
        assertFalse(config.group.on);
    }

    @Test
    void propertiesMissingFromTheFileKeepTheirDefaults() throws IOException {
        // An older file that predates some properties
        this.writeFile("""
                {"count": 7}
                """);

        var config = ConfigurationData.load(TestConfig.class, this.configFile());

        assertEquals(7, config.count);
        assertEquals("default", config.name);
        assertEquals(2, config.group.level);
        assertTrue(this.readFile().has("name"), "the file is rewritten with every property");
    }

    @Test
    void outOfRangeValuesAreClamped() throws IOException {
        // Regression: ranges were only applied when a value was changed in the config screen
        this.writeFile("""
                {"count": 50, "scale": 0, "unbounded": -100, "slider": 11, "doubleSlider": 9, "group": {"level": 9}}
                """);

        var config = ConfigurationData.load(TestConfig.class, this.configFile());

        assertEquals(10, config.count);
        assertEquals(0.5D, config.scale);
        assertEquals(-100D, config.unbounded, "no range, so left alone");
        assertEquals(10, config.slider, "a slider's range applies too");
        assertEquals(4D, config.doubleSlider, "and a double slider's");
        assertEquals(3, config.group.level);
        assertEquals(10, this.readFile().get("count").getAsInt(), "the corrected value is saved");
    }

    @Test
    void nanGetsTheDefault() throws IOException {
        // Regression: NaN survived clamping, then Gson refused to write it and loading failed
        this.writeFile("""
                {"scale": NaN, "unbounded": NaN}
                """);

        var config = ConfigurationData.load(TestConfig.class, this.configFile());

        assertEquals(1D, config.scale);
        assertEquals(-2.5D, config.unbounded);
        assertEquals(-2.5D, this.readFile().get("unbounded").getAsDouble());
    }

    @Test
    void doubleSliderValuesNeedNotBeOnAStep() throws IOException {
        // Only the slider moves in steps; a hand-edited value in range is kept
        this.writeFile("""
                {"doubleSlider": 1.37}
                """);

        var config = ConfigurationData.load(TestConfig.class, this.configFile());

        assertEquals(1.37D, config.doubleSlider);
    }

    @Test
    void unknownEnumValueGetsTheDefault() throws IOException {
        // Gson sets an enum it doesn't recognise (e.g. a renamed constant) to null
        this.writeFile("""
                {"mode": "NO_LONGER_EXISTS"}
                """);

        var config = ConfigurationData.load(TestConfig.class, this.configFile());

        assertEquals(Mode.ALPHA, config.mode);
        assertEquals("ALPHA", this.readFile().get("mode").getAsString());
    }

    @Test
    void nullValuesGetTheirDefaults() throws IOException {
        this.writeFile("""
                {"name": null, "boxedCount": null, "group": null}
                """);

        var config = ConfigurationData.load(TestConfig.class, this.configFile());

        assertEquals("default", config.name);
        assertEquals(3, config.boxedCount);
        assertNotNull(config.group);
        assertEquals(2, config.group.level);
    }

    @Test
    void repairCountsCorrections() {
        var config = new TestConfig();
        config.count = -5;
        config.name = null;
        config.group.level = 0;

        assertEquals(3, ConfigProcessor.repair(spec(), config, "test"));
        assertEquals(0, ConfigProcessor.repair(spec(), config, "test"), "nothing left to correct");
    }

    @Test
    void unreadableFileIsKeptAndDefaultsUsed() throws IOException {
        // Regression: the defaults overwrote the file, losing the user's settings
        var broken = "{\"count\": 7,, \"name\": ";
        this.writeFile(broken);

        var config = ConfigurationData.load(TestConfig.class, this.configFile());

        assertEquals(5, config.count);
        assertEquals(5, this.readFile().get("count").getAsInt(), "a valid file with the defaults replaces it");

        List<Path> backups;
        try (var files = Files.list(this.folder)) {
            backups = files.filter(p -> p.getFileName().toString().endsWith(".bad")).toList();
        }
        assertEquals(1, backups.size(), backups.toString());
        assertTrue(backups.getFirst().getFileName().toString().startsWith("test.json."), backups.toString());
        assertEquals(broken, Files.readString(backups.getFirst()));
    }

    @Test
    void unreadableFileThatCannotBeRenamedIsNotOverwritten() throws IOException {
        // Regression: when the rename failed, the defaults were still written over the file
        var broken = "{\"count\": 7,, \"name\": ";
        this.writeFile(broken);
        // The backup name has a timestamp to the second. A non-empty folder in its place makes the rename fail.
        var now = LocalDateTime.now();
        for (int i = 0; i < 30; i++) {
            var blocker = ConfigurationData.backupPath(this.configFile(), now.plusSeconds(i));
            Files.createDirectories(blocker);
            Files.writeString(blocker.resolve("keep"), "x");
        }

        var config = ConfigurationData.load(TestConfig.class, this.configFile());

        assertEquals(5, config.count, "the defaults are used");
        assertEquals(broken, Files.readString(this.configFile()), "the file is left as it was");
    }

    // ---- Saving ----------------------------------------------------------------------------------------------

    @Test
    void saveWritesValuesAndLeavesNoTemporaryFiles() throws IOException {
        var config = ConfigurationData.load(TestConfig.class, this.configFile());
        config.count = 8;
        config.group.on = false;

        config.save();

        assertEquals(8, this.readFile().get("count").getAsInt());
        assertFalse(this.readFile().getAsJsonObject("group").get("on").getAsBoolean());
        try (var files = Files.list(this.folder)) {
            assertEquals(List.of(this.configFile()), files.toList());
        }
    }

    @Test
    void saveCreatesMissingFolders() throws IOException {
        var nested = this.folder.resolve("a").resolve("b").resolve("test.json");

        ConfigurationData.load(TestConfig.class, nested);

        assertTrue(Files.exists(nested));
    }

    @Test
    void saveRaisesTheChangedEventButLoadDoesNot() {
        var path = this.configFile();
        var events = new AtomicInteger();
        IConfigChangedEvent.EVENT.register(cfg -> {
            if (cfg instanceof TestConfig tc && path.equals(tc.configFilePath))
                events.incrementAndGet();
        });

        var config = ConfigurationData.load(TestConfig.class, path);
        assertEquals(0, events.get(), "loading is not a change");

        config.save();
        assertEquals(1, events.get());
    }

    @Test
    void writeWithoutAFileFails() {
        assertThrows(IOException.class, () -> new TestConfig().write());
    }
}

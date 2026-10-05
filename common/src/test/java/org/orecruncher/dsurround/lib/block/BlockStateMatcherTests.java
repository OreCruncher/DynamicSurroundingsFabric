package org.orecruncher.dsurround.lib.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for block specifications in config files: parsing (including the error messages pack authors see),
 * matching, equality, and writing a matcher back out.
 */
class BlockStateMatcherTests {

    // Need this to bootstrap the Minecraft environment so tests run
    @BeforeAll
    static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---- Helpers ---------------------------------------------------------------------------------------------

    private static BlockStateMatcher matcher(String specification) throws BlockStateParseException {
        return BlockStateMatcher.create(specification, true);
    }

    /**
     * Asserts that parsing fails with a message containing the given text.
     */
    private static void assertRejected(String specification, String expectedMessagePart) {
        Executable parse = () -> matcher(specification);
        var e = assertThrows(BlockStateParseException.class, parse, () -> "expected '" + specification + "' to be rejected");
        assertTrue(e.getMessage().contains(expectedMessagePart),
                () -> "message for '" + specification + "' was: " + e.getMessage());
    }

    // ---- Parsing: accepted -----------------------------------------------------------------------------------

    @Test
    void plainBlockId() throws Exception {
        var result = BlockStateParser.parse("minecraft:stone");

        assertSame(Blocks.STONE, result.block());
        assertFalse(result.hasProperties());
    }

    @Test
    void missingNamespaceMeansMinecraft() throws Exception {
        assertSame(Blocks.STONE, BlockStateParser.parse("stone").block());
    }

    @Test
    void plainAirIsAccepted() throws Exception {
        // Regression: the unknown-block check compared the raw text to "minecraft:air", so "air" was rejected
        assertSame(Blocks.AIR, BlockStateParser.parse("air").block());
    }

    @Test
    void propertiesAreParsedInOrder() throws Exception {
        var result = BlockStateParser.parse("minecraft:oak_stairs[half=top,facing=north]");

        assertEquals(List.of("half", "facing"), List.copyOf(result.properties().keySet()));
        assertEquals(Map.of("half", "top", "facing", "north"), result.properties());
    }

    @Test
    void spacesAreIgnored() throws Exception {
        var result = BlockStateParser.parse("  minecraft:oak_stairs [ half = top ,  facing=north ]  ");

        assertSame(Blocks.OAK_STAIRS, result.block());
        assertEquals(Map.of("half", "top", "facing", "north"), result.properties());
    }

    @Test
    void emptyBracketsMeanNoProperties() throws Exception {
        assertFalse(BlockStateParser.parse("minecraft:oak_log[]").hasProperties());
    }

    // ---- Parsing: rejected, with a message saying why --------------------------------------------------------

    @Test
    void rejectsEmptySpecification() {
        assertRejected("   ", "empty");
    }

    @Test
    void rejectsUnknownBlock() {
        assertRejected("minecraft:not_a_block", "Unknown block");
    }

    @Test
    void rejectsInvalidBlockName() {
        assertRejected("Minecraft:Stone!", "Invalid block name");
    }

    @Test
    void rejectsMissingCloseBracket() {
        // Regression: this specific message was replaced by a generic one
        assertRejected("minecraft:oak_log[axis=y", "Missing ']'");
    }

    @Test
    void rejectsTextAfterCloseBracket() {
        // Regression: trailing text was silently ignored
        assertRejected("minecraft:oak_log[axis=y]oops", "Unexpected text after ']'");
    }

    @Test
    void rejectsCloseBracketWithoutOpen() {
        assertRejected("minecraft:oak_log]", "']' without '['");
    }

    @Test
    void rejectsMissingBlockName() {
        assertRejected("[axis=y]", "Missing block name");
    }

    @Test
    void rejectsPropertyWithoutValue() {
        assertRejected("minecraft:oak_log[axis]", "has no value");
        assertRejected("minecraft:oak_log[axis=]", "has no value");
    }

    @Test
    void rejectsValueWithoutName() {
        assertRejected("minecraft:oak_log[=y]", "has no name");
    }

    @Test
    void rejectsExtraComma() {
        assertRejected("minecraft:oak_stairs[half=top,]", "Empty property entry");
    }

    @Test
    void rejectsPropertyListedTwice() {
        assertRejected("minecraft:oak_log[axis=y,axis=x]", "more than once");
    }

    @Test
    void rejectsInvalidPropertyName() {
        // Regression: this specific message was replaced by a generic one
        assertRejected("minecraft:oak_log[Axis=y]", "Property name 'Axis' is invalid");
    }

    @Test
    void rejectsPropertyTheBlockDoesNotHave() {
        assertRejected("minecraft:oak_log[facing=north]", "Property 'facing' not found");
    }

    @Test
    void rejectsPropertyOnSingleStateBlock() {
        // Regression: properties on a block with one state were silently ignored, typos included
        assertRejected("minecraft:stone[bogus=1]", "Property 'bogus' not found");
    }

    @Test
    void rejectsUnknownValue() {
        assertRejected("minecraft:oak_log[axis=w]", "Value 'w' for property 'axis' not found");
    }

    @Test
    void rejectsTagWhereTagsAreNotAllowed() {
        var e = assertThrows(BlockStateParseException.class, () -> BlockStateMatcher.create("#minecraft:logs", false));
        assertTrue(e.getMessage().contains("tags are not permitted"));
    }

    // ---- Matching --------------------------------------------------------------------------------------------

    @Test
    void blockIdMatchesEveryStateOfTheBlock() throws Exception {
        var m = matcher("minecraft:oak_log");

        for (var state : Blocks.OAK_LOG.getStateDefinition().getPossibleStates())
            assertTrue(m.match(state));
        assertFalse(m.match(Blocks.BIRCH_LOG.defaultBlockState()));
    }

    @Test
    void propertiesMatchOnlyThoseValues() throws Exception {
        var m = matcher("minecraft:oak_log[axis=y]");
        var log = Blocks.OAK_LOG.defaultBlockState();

        assertTrue(m.match(log.setValue(BlockStateProperties.AXIS, Direction.Axis.Y)));
        assertFalse(m.match(log.setValue(BlockStateProperties.AXIS, Direction.Axis.X)));
        assertFalse(m.match(Blocks.BIRCH_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y)));
    }

    @Test
    void unlistedPropertiesCanBeAnything() throws Exception {
        var m = matcher("minecraft:oak_stairs[half=top]");
        var stairs = Blocks.OAK_STAIRS.defaultBlockState();

        assertTrue(m.match(stairs.setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, Direction.EAST)));
        assertTrue(m.match(stairs.setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, Direction.WEST)));
        assertFalse(m.match(stairs.setValue(StairBlock.HALF, Half.BOTTOM)));
    }

    @Test
    void propertiesDoNotMatchAStateWithoutThem() {
        var props = new org.orecruncher.dsurround.lib.block.BlockStateProperties(Blocks.OAK_LOG.defaultBlockState());

        // A different block without an axis property: false, not an exception
        assertFalse(props.matches(Blocks.STONE.defaultBlockState()));
    }

    // ---- Equality --------------------------------------------------------------------------------------------

    @Test
    void sameSpecificationWrittenDifferentlyIsEqual() throws Exception {
        var a = matcher("minecraft:oak_stairs[facing=north,half=top]");
        var b = matcher("minecraft:oak_stairs[ half = top , facing = north ]");

        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void blockMatcherAndStateMatcherAreNotEqual() throws Exception {
        // Regression: MatchOnBlock.equals accepted its subclass, so equality wasn't symmetric
        var whole = matcher("minecraft:oak_log");
        var partial = matcher("minecraft:oak_log[axis=y]");

        assertNotEquals(whole, partial);
        assertNotEquals(partial, whole);
    }

    @Test
    void subsetOfPropertiesIsNotEqual() throws Exception {
        // Regression: equality was "the other's properties are a subset of mine", which isn't symmetric
        var fewer = matcher("minecraft:oak_stairs[half=top]");
        var more = matcher("minecraft:oak_stairs[half=top,facing=north]");

        assertNotEquals(fewer, more);
        assertNotEquals(more, fewer);
    }

    @Test
    void stateMatcherEqualsParsedFullSpecification() throws Exception {
        // Built from a state's own property map (a fastutil map) vs from parsed text: equal, with equal hashes
        var state = Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        var fromState = new MatchOnBlockState(state);
        var parsed = matcher("minecraft:oak_log[axis=z]");

        assertEquals(fromState, parsed);
        assertEquals(parsed, fromState);
        assertEquals(fromState.hashCode(), parsed.hashCode());
    }

    @Test
    void tagMatchersCompareByTag() throws Exception {
        assertEquals(matcher("#minecraft:logs"), matcher("#minecraft:logs"));
        assertNotEquals(matcher("#minecraft:logs"), matcher("#minecraft:leaves"));
        assertTrue(matcher("#minecraft:logs").isTagMatcher());
        assertFalse(matcher("minecraft:oak_log").isTagMatcher());
    }

    // ---- Writing back out ------------------------------------------------------------------------------------

    @Test
    void specificationsParseBackToEqualMatchers() throws Exception {
        var specifications = List.of(
                "minecraft:stone",
                "minecraft:oak_log",
                "minecraft:oak_log[axis=y]",
                "minecraft:oak_stairs[half=top,facing=north]",
                "minecraft:oak_stairs[waterlogged=true]",
                "#minecraft:logs");

        for (var spec : specifications) {
            var original = matcher(spec);
            var written = original.toSpecification();
            assertEquals(spec, written, "written form of " + spec);
            assertEquals(original, matcher(written), "round trip of " + spec);
        }
    }

    @Test
    void writtenFormIsNormalized() throws Exception {
        assertEquals("minecraft:oak_stairs[half=top,facing=north]", matcher(" oak_stairs[ half = top , facing = north ] ").toSpecification());
        assertEquals("#dsurround:logs", matcher("#logs").toSpecification(), "a tag without a namespace is one of the mod's tags");
    }

    @Test
    void codecReadsAndWritesSpecifications() {
        // Regression: the codec wrote toString() output ("BlockStateMatcher{Block{...}}") that couldn't be read back
        for (var spec : List.of("minecraft:oak_log[axis=y]", "#minecraft:logs", "minecraft:stone")) {
            var decoded = BlockStateMatcher.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(spec)).getOrThrow();
            var encoded = BlockStateMatcher.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow();
            assertEquals(spec, encoded.getAsString());
        }
    }

    // ---- The mod's own configs -------------------------------------------------------------------------------

    /**
     * Every block specification in the bundled blocks.json and sound_mappings.json files. Collected from any
     * "blocks" array, wherever it appears.
     */
    private static List<String> bundledSpecifications() throws IOException {
        var specs = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources"))) {
            for (var file : files.filter(f -> f.endsWith("blocks.json") || f.endsWith("sound_mappings.json")).toList())
                collectBlocks(JsonParser.parseString(Files.readString(file)), specs);
        }
        return specs;
    }

    private static void collectBlocks(JsonElement element, List<String> specs) {
        if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("blocks") && entry.getValue().isJsonArray())
                    entry.getValue().getAsJsonArray().forEach(e -> specs.add(e.getAsString()));
                else
                    collectBlocks(entry.getValue(), specs);
            }
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(e -> collectBlocks(e, specs));
        }
    }

    @Test
    void bundledConfigsParse() throws Exception {
        var specs = bundledSpecifications();
        assertFalse(specs.isEmpty(), "no bundled block specifications found");

        int checked = 0;
        for (var spec : specs) {
            // Blocks from other mods can't be checked here (the mod isn't loaded); tags always can
            boolean isTag = spec.startsWith(BlockStateMatcher.TAG_TYPE);
            if (!isTag && spec.contains(":") && !spec.startsWith("minecraft:"))
                continue;
            var m = matcher(spec);
            assertEquals(m, matcher(m.toSpecification()), "round trip of bundled " + spec);
            checked++;
        }
        assertTrue(checked > 0);
    }

    @Test
    void codecReportsTheParseError() {
        var result = BlockStateMatcher.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("minecraft:oak_log[axis=w]"));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().get().message().contains("Value 'w' for property 'axis' not found"));
    }
}

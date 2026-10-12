package org.orecruncher.dsurround.config.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.codec.CodecExtensions;
import org.orecruncher.dsurround.sound.SoundFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for decoding the configuration records, including every configuration file the mod ships.
 */
public class ConfigDataTests {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static <T> DataResult<T> parse(Codec<T> codec, String text) {
        return codec.parse(JsonOps.INSTANCE, json(text));
    }

    private static String errorOf(DataResult<?> result) {
        return result.error().map(DataResult.Error::message).orElse("");
    }

    // ---- Weights ---------------------------------------------------------------------------------------------

    @Test
    void negativeWeightIsAnErrorNotAnException() {
        // Regression: WeightValue.of threw, which escaped the codec and abandoned the whole file
        var result = assertDoesNotThrow(() -> parse(AcousticConfig.CODEC, "{\"factory\": \"dsurround:a\", \"weight\": -1}"));

        assertTrue(result.isError());
        assertTrue(errorOf(result).contains("must be from 0 to"), errorOf(result));
    }

    @Test
    void hugeWeightIsAnError() {
        // Regression: weights had no upper limit, so a few huge ones overflowed the total and nothing was chosen
        var result = assertDoesNotThrow(() -> parse(AcousticConfig.CODEC, "{\"factory\": \"dsurround:a\", \"weight\": 2000000000}"));

        assertTrue(result.isError());
        assertTrue(errorOf(result).contains("must be from 0 to"), errorOf(result));
    }

    @Test
    void badWeightFallsBackAndTheRestOfTheFileLoads() {
        // Regression: the exception from a negative weight lost every entry in the file. Now the bad optional
        // field takes its default (logged as a warning) and everything else loads.
        var content = """
                [
                  {"factory": "dsurround:first", "weight": 5},
                  {"factory": "dsurround:bad", "weight": -1},
                  {"factory": "dsurround:third"}
                ]
                """;

        var result = CodecExtensions.deserialize("test.json", content, Codec.list(AcousticConfig.CODEC)).orElseThrow();

        assertEquals(List.of("dsurround:first", "dsurround:bad", "dsurround:third"),
                result.stream().map(a -> a.factory().toString()).toList());
        assertEquals(List.of(5, 10, 10), result.stream().map(a -> a.weight().asInt()).toList(),
                "the bad weight became the default, 10");
    }

    @Test
    void badRequiredFieldDropsOnlyThatEntry() {
        var content = """
                [
                  {"factory": "dsurround:first"},
                  {"weight": 5},
                  {"factory": "dsurround:third"}
                ]
                """;

        var result = CodecExtensions.deserialize("test.json", content, Codec.list(AcousticConfig.CODEC)).orElseThrow();

        assertEquals(List.of("dsurround:first", "dsurround:third"), result.stream().map(a -> a.factory().toString()).toList());
    }

    @Test
    void zeroWeightIsAllowed() {
        // A valid way to switch an entry off
        assertTrue(parse(AcousticConfig.CODEC, "{\"factory\": \"dsurround:a\", \"weight\": 0}").isSuccess());
    }

    // ---- Sound mappings --------------------------------------------------------------------------------------

    private static DataResult<SoundMappingConfigRule> mapping(String rules) {
        return parse(SoundMappingConfigRule.CODEC, "{\"soundEvent\": \"minecraft:block.stone.step\", \"rules\": " + rules + "}");
    }

    @Test
    void validMappingsLoad() {
        assertTrue(mapping("[{\"factory\": \"dsurround:d\"}]").isSuccess(), "only a default");
        assertTrue(mapping("[{\"blocks\": [\"minecraft:stone\"], \"factory\": \"dsurround:a\"}]").isSuccess(), "only block rules");
        assertTrue(mapping("""
                [{"blocks": ["minecraft:stone"], "factory": "dsurround:a"},
                 {"blocks": ["minecraft:dirt"], "factory": "dsurround:b"},
                 {"factory": "dsurround:d"}]""").isSuccess(), "block rules then the default");
    }

    @Test
    void mappingWithoutRulesIsRejected() {
        // Regression: accepted, then merging another file's mapping into it threw NullPointerException
        var result = mapping("[]");

        assertTrue(result.isError());
        assertTrue(errorOf(result).contains("has no rules"), errorOf(result));
    }

    @Test
    void defaultRuleMustBeLast() {
        // Regression: accepted, then merging threw "Last rule ... is not default" and rules after it never matched
        var result = mapping("[{\"factory\": \"dsurround:d\"}, {\"blocks\": [\"minecraft:stone\"], \"factory\": \"dsurround:a\"}]");

        assertTrue(result.isError());
        assertTrue(errorOf(result).contains("must be the only one and last"), errorOf(result));
    }

    @Test
    void onlyOneDefaultRule() {
        assertTrue(mapping("[{\"factory\": \"dsurround:d\"}, {\"factory\": \"dsurround:e\"}]").isError());
    }

    @Test
    void emptyBlockListIsADefault() {
        assertTrue(mapping("[{\"blocks\": [], \"factory\": \"dsurround:d\"}, {\"factory\": \"dsurround:e\"}]").isError(),
                "\"blocks\": [] counts as a default rule too");
    }

    // ---- Sound metadata --------------------------------------------------------------------------------------

    @Test
    void categoryIgnoresCase() {
        var result = parse(SoundMetadataConfig.CODEC, "{\"ds_category\": \"Block\"}").getOrThrow();

        assertEquals(SoundSource.BLOCKS, result.category().orElseThrow());
    }

    @Test
    void unknownCategoryKeepsTheRestOfTheEntry() {
        // Regression: an unknown category failed the entry, losing its title and credits
        var result = parse(SoundMetadataConfig.CODEC, """
                {"ds_title": "dsurround.title", "ds_category": "blokcs",
                 "ds_credits": [{"name": "Rain", "author": "Someone", "license": "CC0"}]}
                """).getOrThrow();

        assertEquals(SoundSource.AMBIENT, result.category().orElseThrow());
        assertEquals("dsurround.title", result.title().orElseThrow());
        assertEquals(1, result.credits().size());
        assertFalse(result.isDefault());
    }

    // ---- Dimensions ------------------------------------------------------------------------------------------

    @Test
    void dimensionDumpLabelsEachValue() {
        // Regression: skyHeight was labelled "seaLevel"
        var rule = parse(DimensionConfigRule.CODEC, "{\"dimId\": \"minecraft:overworld\", \"seaLevel\": 63, \"skyHeight\": 256}").getOrThrow();

        var text = rule.toString();
        assertTrue(text.contains("seaLevel: 63"), text);
        assertTrue(text.contains("skyHeight: 256"), text);
    }

    @Test
    void dimensionRulesCompareAllTheirValues() {
        // Equality used to look at the dimension only
        var a = parse(DimensionConfigRule.CODEC, "{\"dimId\": \"minecraft:overworld\", \"seaLevel\": 63}").getOrThrow();
        var b = parse(DimensionConfigRule.CODEC, "{\"dimId\": \"minecraft:overworld\", \"seaLevel\": 40}").getOrThrow();
        var c = parse(DimensionConfigRule.CODEC, "{\"dimId\": \"minecraft:overworld\", \"seaLevel\": 63}").getOrThrow();

        assertNotEquals(a, b);
        assertEquals(a, c);
    }

    @Test
    void dimensionRulesSetWeatherAndWind() {
        var rule = parse(DimensionConfigRule.CODEC, "{\"dimId\": \"twilightforest:twilight_forest\", \"particleWeather\": true, \"simulateWind\": false, \"groundFog\": true}").getOrThrow();
        assertEquals(Optional.of(true), rule.particleWeather());
        assertEquals(Optional.of(false), rule.simulateWind());
        assertEquals(Optional.of(true), rule.groundFog());
        var text = rule.toString();
        assertTrue(text.contains("particleWeather: true") && text.contains("simulateWind: false") && text.contains("groundFog: true"), text);

        // Left out, they are left to the dimension's own guess
        var plain = parse(DimensionConfigRule.CODEC, "{\"dimId\": \"minecraft:overworld\"}").getOrThrow();
        assertTrue(plain.particleWeather().isEmpty() && plain.simulateWind().isEmpty() && plain.groundFog().isEmpty());
    }

    @Test
    void everyWindLoopFollowsTheWind() throws IOException {
        // The wind sounds play as loud as the wind blows only through their factories' followsWind: a biome rule
        // naming one without a factory would get a default factory, at a steady volume
        var factories = Codec.list(SoundFactory.CODEC).parse(JsonOps.INSTANCE,
                json(resource("/assets/dsurround/dsconfigs/sound_factories.json"))).getOrThrow();
        var followsWind = factories.stream().filter(SoundFactory::followsWind)
                .map(f -> f.location().orElseThrow().toString()).collect(java.util.stream.Collectors.toSet());
        assertFalse(followsWind.isEmpty());

        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/resources/assets"))) {
            var biomeFiles = files.filter(f -> f.getFileName().toString().equals("biomes.json")).toList();
            assertFalse(biomeFiles.isEmpty());
            for (var file : biomeFiles) {
                for (var rule : json(java.nio.file.Files.readString(file)).getAsJsonArray()) {
                    var acoustics = rule.getAsJsonObject().getAsJsonArray("acoustics");
                    if (acoustics == null)
                        continue;
                    for (var acoustic : acoustics) {
                        var factory = acoustic.getAsJsonObject().get("factory").getAsString();
                        var id = factory.contains(":") ? factory : "dsurround:" + factory;
                        if (id.startsWith("dsurround:biome.wind"))
                            assertTrue(followsWind.contains(id), file + ": " + id);
                    }
                }
            }
        }
    }

    @Test
    void theTwilightForestIsAtSeaLevelZero() throws IOException {
        // Regression: the client always reports a sea level of 63 (the server's world generation setting isn't sent),
        // so in the Twilight Forest, whose sea level is 0, much of the surface counted as underground
        var rules = Codec.list(DimensionConfigRule.CODEC).parse(JsonOps.INSTANCE,
                json(resource("/assets/twilightforest/dsconfigs/dimensions.json"))).getOrThrow();
        var rule = rules.stream().filter(r -> r.dimensionId().toString().equals("twilightforest:twilight_forest")).findFirst().orElseThrow();
        assertEquals(Optional.of(0), rule.seaLevel());
    }

    @Test
    void theNetherAndEndHaveNoWindOrGroundFog() throws IOException {
        var rules = Codec.list(DimensionConfigRule.CODEC).parse(JsonOps.INSTANCE,
                json(resource("/assets/dsurround/dsconfigs/dimensions.json"))).getOrThrow();
        for (var dimension : List.of("minecraft:the_nether", "minecraft:the_end")) {
            var rule = rules.stream().filter(r -> r.dimensionId().toString().equals(dimension)).findFirst().orElseThrow();
            assertEquals(Optional.of(false), rule.simulateWind(), dimension);
            assertEquals(Optional.of(false), rule.groundFog(), dimension);
        }
    }

    // ---- Error reporting -------------------------------------------------------------------------------------

    @Test
    void unparseableContentGivesNothing() {
        var result = assertDoesNotThrow(() -> CodecExtensions.deserialize("broken.json", "{not json", Codec.list(AcousticConfig.CODEC)));

        assertTrue(result.isEmpty());
    }

    // ---- The mod's own configuration files -------------------------------------------------------------------

    private static String resource(String path) throws IOException {
        try (InputStream in = ConfigDataTests.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static <T> void assertLoadsCleanly(String path, Codec<T> codec) throws IOException {
        var result = codec.parse(JsonOps.INSTANCE, json(resource(path)));
        assertTrue(result.isSuccess(), path + ": " + errorOf(result));
    }

    @Test
    void bundledConfigFilesLoadWithoutErrors() throws IOException {
        // Every entry, so the stricter checks reject nothing the mod ships
        assertLoadsCleanly("/assets/dsurround/dsconfigs/biomes.json", Codec.list(BiomeConfigRule.CODEC));
        assertLoadsCleanly("/assets/dsurround/dsconfigs/blocks.json", Codec.list(BlockConfigRule.CODEC));
        assertLoadsCleanly("/assets/dsurround/dsconfigs/dimensions.json", Codec.list(DimensionConfigRule.CODEC));
        assertLoadsCleanly("/assets/dsurround/dsconfigs/sound_mappings.json", Codec.list(SoundMappingConfigRule.CODEC));
        assertLoadsCleanly("/assets/dsurround/dsconfigs/sound_factories.json", Codec.list(SoundFactory.CODEC));
        assertLoadsCleanly("/assets/dsurround/sounds.json", Codec.unboundedMap(Codec.STRING, SoundMetadataConfig.CODEC));
    }

    private static List<String> tagEntries(String path) throws IOException {
        var result = TagFile.CODEC.parse(JsonOps.INSTANCE, json(resource(path)));
        assertTrue(result.isSuccess(), path + ": " + errorOf(result));
        // A TagEntry prints as its id, with # in front for a tag reference
        return result.getOrThrow().entries().stream().map(TagEntry::toString).toList();
    }

    @Test
    void villagersTagHasTheVillagerTypes() throws IOException {
        // Neither vanilla nor the c: convention tags have a villager tag, so the mod has its own
        var entries = tagEntries("/assets/dsurround/dsconfigs/tags/entity_type/villagers.json");

        assertEquals(List.of("minecraft:villager", "minecraft:wandering_trader"), entries);
        for (var entry : entries)
            assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(entry)), entry + " isn't an entity type");
    }

    @Test
    void frostBreathUsesTheVillagersTag() throws IOException {
        var entries = tagEntries("/assets/dsurround/dsconfigs/tags/entity_type/effects/frost_breath.json");

        assertTrue(entries.contains("#dsurround:villagers"), entries.toString());
        assertFalse(entries.contains("minecraft:villager"), "listed through the tag now");
    }
}

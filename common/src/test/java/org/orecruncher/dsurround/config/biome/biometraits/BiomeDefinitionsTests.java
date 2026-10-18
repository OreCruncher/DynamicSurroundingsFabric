package org.orecruncher.dsurround.config.biome.biometraits;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.lib.resources.ModRoots;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.orecruncher.dsurround.config.BiomeTrait.*;

/**
 * Tests for reading what a biome grows from its definition file, and the traits that gives. The files are on the
 * class path: vanilla's in the game's jar, made-up ones under {@code data/dsurroundtest} in the test resources.
 */
public class BiomeDefinitionsTests {

    static {
        // BiomeTrait reaches the game's registries
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private final BiomeDefinitions definitions = new BiomeDefinitions(IDefinitionFiles.classPath(BiomeDefinitionsTests.class.getClassLoader()));

    private BiomeDefinition read(String namespace, String path) {
        var result = this.definitions.read(Identifier.fromNamespaceAndPath(namespace, path));
        assertTrue(result.isPresent(), namespace + ":" + path + " has a definition");
        return result.get();
    }

    private static Set<BiomeTrait> traits(BiomeDefinition definition) {
        return BiomeDefinitionAnalyzer.traits(definition);
    }

    // ---- Reading

    @Test
    void treesAreFoundByTheirPlacedFeaturesName() {
        // Regression: Biomes O' Plenty's Mediterranean forest places "trees_mediterranean_forest", whose configured
        // feature (registered in code, so no file) is misspelled "tress_"
        var forest = this.read("dsurroundtest", "typo_forest");
        assertEquals(2.1F, forest.treesPerChunk(), 1e-4F);
        assertFalse(forest.aquatic());
    }

    @Test
    void aSelectorAmongTreesIsTrees() {
        // Named for nothing: its configured feature picks among placed features, one of them vanilla's birch, whose
        // configured feature is a minecraft:tree (read from the game's jar)
        assertEquals(6F, this.read("dsurroundtest", "selector_woods").treesPerChunk(), 1e-4F);
    }

    @Test
    void bushesArentTrees() {
        // Biomes O' Plenty's rocky shrubland places "trees_rocky_shrubland", six oak bushes a chunk: no forest
        assertEquals(0F, this.read("dsurroundtest", "shrubland").treesPerChunk());
    }

    @Test
    void aRarityFilterDividesTheCount() {
        assertEquals(0.5F, this.read("dsurroundtest", "rare_trees").treesPerChunk(), 1e-4F);
    }

    @Test
    void aCountThatCantBeWorkedOutLeavesTheTreesUnknown() {
        assertFalse(this.read("dsurroundtest", "odd_count").treesKnown(), "a mod's own count rule");
        assertFalse(this.read("dsurroundtest", "missing_trees").treesKnown(), "a tree feature with no file");
    }

    @Test
    void featuresThatArentTreesArentCounted() {
        // A flower patch with no file, and a tag
        var meadow = this.read("dsurroundtest", "meadow");
        assertEquals(0F, meadow.treesPerChunk());
        assertTrue(traits(meadow).isEmpty(), "no trees says nothing");
    }

    @Test
    void seagrassOrKelpIsWater() {
        assertTrue(this.read("dsurroundtest", "marsh").aquatic());
    }

    @Test
    void waterCreaturesAreWater() {
        // Frozen over, a river grows no seagrass, but its squid show it is water
        var river = this.read("dsurroundtest", "icy_river");
        assertTrue(river.aquatic());
        assertTrue(traits(river).isEmpty(), "its rare tree doesn't make it sparse");
    }

    @Test
    void definitionsAreReadFromAJarAndFromAFolder(@TempDir Path folder) throws Exception {
        // A mod's path may be its jar (NeoForge on 26.2), where Architectury's findResource looks beside it, so no
        // definition would be read. A jar is opened; a folder (a development run, a jar's root on Fabric) is
        // read as it is.
        var file = IDefinitionFiles.resource("jarmod", "placed_feature", "trees_jar");
        var content = "{\"feature\":\"jarmod:trees\",\"placement\":[{\"type\":\"minecraft:count\",\"count\":7}]}";
        var jar = folder.resolve("jarmod.jar");
        try (var out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry(file));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        var unpacked = folder.resolve("unpacked");
        Files.createDirectories(unpacked.resolve(file).getParent());
        Files.writeString(unpacked.resolve(file), content);

        for (var path : List.of(jar, unpacked)) {
            var files = IDefinitionFiles.under(ns -> ModRoots.of(List.of(path)));
            var placed = files.read("jarmod", "placed_feature", "trees_jar");
            assertTrue(placed.isPresent(), "read from " + path.getFileName());
            assertEquals(7F, BiomeDefinitions.count(placed.get().getAsJsonObject()));
            assertTrue(files.read("jarmod", "placed_feature", "absent").isEmpty());
        }
        assertTrue(ModRoots.of(List.of(folder.resolve("nothing.jar"))).isEmpty(), "a path that isn't there");
    }

    @Test
    void aBiomeWithNoFileHasNoDefinition() {
        assertTrue(this.definitions.read(Identifier.fromNamespaceAndPath("dsurroundtest", "nowhere")).isEmpty());
    }

    @Test
    void countsAreTheMeansOfTheirProviders() {
        assertEquals(3F, BiomeDefinitions.mean(JsonParser.parseString("3")));
        assertEquals(5F, BiomeDefinitions.mean(JsonParser.parseString("{\"type\":\"minecraft:constant\",\"value\":5}")));
        assertEquals(6F, BiomeDefinitions.mean(JsonParser.parseString("{\"type\":\"minecraft:uniform\",\"min_inclusive\":4,\"max_inclusive\":8}")));
        assertEquals(6.1F, BiomeDefinitions.mean(JsonParser.parseString(
                "{\"type\":\"minecraft:weighted_list\",\"distribution\":[{\"data\":6,\"weight\":9},{\"data\":7,\"weight\":1}]}")), 1e-4F);
        assertEquals(2F, BiomeDefinitions.mean(JsonParser.parseString(
                "{\"type\":\"minecraft:clamped\",\"source\":5,\"min_inclusive\":0,\"max_inclusive\":2}")));
        assertTrue(Float.isNaN(BiomeDefinitions.mean(JsonParser.parseString(
                "{\"type\":\"minecraft:clamped\",\"source\":5,\"min_inclusive\":3,\"max_inclusive\":2}"))), "bounds the wrong way round");
        assertTrue(Float.isNaN(BiomeDefinitions.mean(JsonParser.parseString("{\"type\":\"some_mod:odd\"}"))));
        assertTrue(Float.isNaN(BiomeDefinitions.mean(JsonParser.parseString("\"text\""))));
    }

    @Test
    void noiseCountsAreAveraged() {
        var threshold = JsonParser.parseString("{\"placement\":[{\"type\":\"minecraft:noise_threshold_count\",\"noise_level\":-0.8,\"below_noise\":5,\"above_noise\":10}]}");
        assertEquals(7.5F, BiomeDefinitions.count(threshold.getAsJsonObject()));
        var based = JsonParser.parseString("{\"placement\":[{\"type\":\"minecraft:noise_based_count\",\"noise_to_count_ratio\":160,\"noise_factor\":80,\"noise_offset\":0.3}]}");
        assertEquals(48F, BiomeDefinitions.count(based.getAsJsonObject()), 1e-4F);
    }

    // ---- Traits, set by vanilla's

    @Test
    void manyTreesAreADenseForestAndFewSparse() {
        assertEquals(Set.of(FOREST, DENSE_VEGETATION), traits(new BiomeDefinition(9.1F, false)));
        assertEquals(Set.of(SPARSE_VEGETATION), traits(new BiomeDefinition(2.1F, false)));
        assertEquals(Set.of(), traits(new BiomeDefinition(3.1F, false)), "in between says nothing");
        assertEquals(Set.of(), traits(new BiomeDefinition(2.1F, true)), "few trees in water aren't sparse");
        // Regression: Biomes O' Plenty's wetland and tropics, water by their seagrass, lost their dense vegetation
        assertEquals(Set.of(DENSE_VEGETATION), traits(new BiomeDefinition(9.1F, true)), "many trees in water are dense, not a forest");
        assertEquals(Set.of(), traits(new BiomeDefinition(Float.NaN, false)));
    }

    @Test
    void vanillasDefinitionsAgreeWithTheVanillaTable() throws IllegalAccessException {
        // What the definitions of vanilla's biomes say, against the table worked out by hand. Two cave biomes place
        // the plains' trees on the surface above them, and the mangrove swamp's many mangroves make a forest of it
        // (its kind is a swamp, from its tags, before this is looked at). Frozen oceans and rivers grow no seagrass
        // but spawn squid, so they are water and not sparse.
        var notSparse = Set.of("deep_dark", "dripstone_caves");
        var notForest = Set.of("mangrove_swamp");
        var vanilla = new VanillaBiomeAnalyzer();
        var checked = new ArrayList<String>();
        for (var key : vanillaBiomes()) {
            var id = key.identifier();
            var definition = this.definitions.read(id);
            assertTrue(definition.isPresent(), id + " has a definition in the game's jar");
            assertTrue(definition.get().treesKnown(), id + "'s trees are counted");
            var table = EnumSet.noneOf(BiomeTrait.class);
            vanilla.analyze(evidence(id), table);
            var found = traits(definition.get());
            if (found.contains(DENSE_VEGETATION))
                assertTrue(table.contains(DENSE_VEGETATION), id + " is dense in the table");
            if (found.contains(SPARSE_VEGETATION) && !notSparse.contains(id.getPath()))
                assertTrue(table.contains(SPARSE_VEGETATION), id + " is sparse in the table");
            if (found.contains(FOREST) && !notForest.contains(id.getPath()))
                assertTrue(table.contains(FOREST), id + " is a forest in the table");
            if (table.contains(FOREST) && !table.contains(SPARSE_VEGETATION))
                assertTrue(found.contains(FOREST), id + ", a dense forest in the table, is found to be one");
            checked.add(id.getPath());
        }
        assertTrue(checked.size() >= 60, "most of vanilla's biomes: " + checked.size());
    }

    private static List<ResourceKey<Biome>> vanillaBiomes() throws IllegalAccessException {
        var result = new ArrayList<ResourceKey<Biome>>();
        for (var field : Biomes.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.get(null) instanceof ResourceKey<?> key
                    && key.isFor(Registries.BIOME))
                result.add(key.cast(Registries.BIOME).orElseThrow());
        }
        return result;
    }

    private static BiomeEvidence evidence(Identifier id) {
        return new BiomeEvidence(id, null, true, 0.5F, false, 0.5F, BiomeSpecialEffects.GrassColorModifier.NONE,
                Optional.empty(), Optional.empty(), Optional.empty());
    }
}

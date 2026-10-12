package org.orecruncher.dsurround.config.biome;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptEngine;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for which biomes the shipped biome rules fill with fog, by running the rules' selectors against sets of traits.
 */
public class BiomeFogRuleTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private record FogRule(String selector, String density) {
    }

    private static List<FogRule> fogRules() throws Exception {
        try (var stream = Objects.requireNonNull(BiomeFogRuleTests.class.getResourceAsStream("/assets/dsurround/dsconfigs/biomes.json"))) {
            var rules = new ArrayList<FogRule>();
            for (var element : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray()) {
                JsonObject rule = element.getAsJsonObject();
                if (rule.has("fogDensity"))
                    rules.add(new FogRule(rule.get("biomeSelector").getAsString(), rule.get("fogDensity").getAsString()));
            }
            assertFalse(rules.isEmpty());
            return rules;
        }
    }

    /**
     * The fog densities the rules give a biome with these traits, and no others; a selector reading anything but the
     * traits fails the test rather than guessing
     */
    private static List<String> fogFor(Set<BiomeTrait> traits) throws Exception {
        var engine = new ScriptEngine();
        // As the selectors write them
        for (var trait : BiomeTrait.values())
            engine.defineVariable(trait.name(), () -> traits.contains(trait));
        // Rules naming biomes by id or name (frozen oceans, the void) name none of these
        engine.property("biome.getId", () -> "test:biome");
        engine.property("biome.getName", () -> "Test Biome");
        var densities = new ArrayList<String>();
        for (var rule : fogRules()) {
            var result = engine.compile(rule.selector()).eval();
            if (Boolean.TRUE.equals(result))
                densities.add(rule.density());
        }
        return densities;
    }

    @Test
    void aSpookyForestIsFoggy() throws Exception {
        assertEquals(List.of("heavy"), fogFor(Set.of(BiomeTrait.OVERWORLD, BiomeTrait.FOREST, BiomeTrait.SPOOKY, BiomeTrait.DENSE_VEGETATION)));
    }

    @Test
    void aSpookyCaveIsNot() throws Exception {
        // Regression: Biomes O' Plenty's spider nest is spooky and underground, and filled with heavy fog, thickening
        // with depth as more of the area around the player became it
        assertEquals(List.of(), fogFor(Set.of(BiomeTrait.OVERWORLD, BiomeTrait.UNDERGROUND, BiomeTrait.CAVE, BiomeTrait.SPOOKY)));
    }

    @Test
    void theDeepDarkIsNot() throws Exception {
        // It has a darkness of its own
        assertEquals(List.of(), fogFor(Set.of(BiomeTrait.OVERWORLD, BiomeTrait.UNDERGROUND, BiomeTrait.CAVE, BiomeTrait.SPOOKY,
                BiomeTrait.RARE, BiomeTrait.TEMPERATE)));
    }
}

package org.orecruncher.dsurround.config.biome;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.ConfigServices;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.threading.RecordingLog;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.testing.Fakes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.orecruncher.dsurround.config.BiomeTrait.*;

/**
 * Tests for which kind of water the scanner takes a biome for when the player is under it.
 */
public class BiomeInfoOceanTests {

    static {
        // BiomeTrait reaches the game's registries
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BiomeInfo info(BiomeTrait... traits) {
        var services = new ConfigServices(new RecordingLog(), Fakes.of(ISoundLibrary.class, Map.of()),
                Fakes.of(ITagLibrary.class, Map.of()), Fakes.of(IConditionEvaluator.class, Map.of()));
        return new BiomeInfoBuilder(1, ResourceLocation.fromNamespaceAndPath("test", "biome"), "biome",
                BiomeTraits.of(traits), services).build();
    }

    @Test
    void aShallowOceanIsAnOcean() {
        // Regression: the convention tags make vanilla's oceans shallow, cleanup took their OCEAN away, and under
        // them the player heard plain water rather than the ocean
        assertTrue(info(OVERWORLD, SHALLOW_OCEAN).isOcean());
        assertTrue(info(OVERWORLD, OCEAN).isOcean());
    }

    @Test
    void aDeepOceanIsDeep() {
        var deep = info(OVERWORLD, DEEP_OCEAN);
        assertTrue(deep.isDeepOcean());
        assertFalse(deep.isOcean());
    }

    @Test
    void aRiverIsntAnOcean() {
        assertFalse(info(OVERWORLD, RIVER).isOcean());
    }
}

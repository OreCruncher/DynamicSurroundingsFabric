package org.orecruncher.dsurround.runtime.variables;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.math.Motion;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the player and biome script variables.
 */
public class VariableTests {

    @BeforeAll
    static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Biome biome(boolean hasPrecipitation, float temperature) {
        return new Biome.BiomeBuilder()
                .hasPrecipitation(hasPrecipitation)
                .temperature(temperature)
                .downfall(0.5F)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .waterColor(0)
                        .build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
    }

    // ---- player.isSuffocating ----------------------------------------------------------------------------------

    @Test
    void suffocatingWhenOutOfAir() {
        // Regression: only below zero counted, so it dropped out each time drowning damage reset the air to 0
        assertTrue(PlayerVariables.isSuffocating(false, 0));
        assertTrue(PlayerVariables.isSuffocating(false, -5));
        assertTrue(PlayerVariables.isSuffocating(false, -20));
    }

    @Test
    void notSuffocatingWithAirLeft() {
        assertFalse(PlayerVariables.isSuffocating(false, 1));
        assertFalse(PlayerVariables.isSuffocating(false, 300));
    }

    @Test
    void neverSuffocatingInCreative() {
        assertFalse(PlayerVariables.isSuffocating(true, -20));
    }

    // ---- player.isMoving ------------------------------------------------------------------------------------------

    @Test
    void movingIsActualMovement() {
        // Regression: view bobbing was compared, which kept "moving" true for many ticks after stopping and was
        // false while flying, falling or swimming forward
        assertTrue(Motion.isMovingHorizontally(0.2D, 0D));
        assertTrue(Motion.isMovingHorizontally(0D, -0.05D));
        assertFalse(Motion.isMovingHorizontally(0D, 0D));
        assertFalse(Motion.isMovingHorizontally(0.0005D, -0.0005D), "interpolation jitter isn't movement");
    }

    // ---- biome.getPrecipitationType -------------------------------------------------------------------------------

    @Test
    void biomeRulesSeeTheBiomesOwnPrecipitation() {
        // Regression: biome rules looked precipitation up where the player stood when the libraries reloaded, so
        // altitude (or being at the menu) changed what a rule decided
        var variables = BiomeVariables.forBiomeRules(null);

        variables.setBiome(biome(true, 0.8F), null);
        assertEquals("RAIN", variables.getPrecipitationType());

        variables.setBiome(biome(true, 0.0F), null);
        assertEquals("SNOW", variables.getPrecipitationType());

        variables.setBiome(biome(false, 2.0F), null);
        assertEquals("NONE", variables.getPrecipitationType());
    }

    @Test
    void biomeRulePrecipitationNeedsNoWorld() {
        // Seasons mods change Biome.getPrecipitationAt() to read the client's level, which isn't there at the menu
        // (or in this test, which has Serene Seasons on the classpath). Rules work from the biome alone.
        assertDoesNotThrow(() -> BiomeVariables.biomePrecipitation(biome(true, 0.8F)));
    }

    @Test
    void rainThresholdMatchesVanilla() {
        assertEquals(Biome.Precipitation.RAIN, BiomeVariables.biomePrecipitation(biome(true, 0.15F)));
        assertEquals(Biome.Precipitation.SNOW, BiomeVariables.biomePrecipitation(biome(true, 0.14F)));
    }

    @Test
    void noBiomeMeansNoPrecipitation() {
        var variables = BiomeVariables.forBiomeRules(null);
        variables.setBiome(null, null);

        assertEquals("NONE", variables.getPrecipitationType());
    }

    @Test
    void unchangedBiomeKeepsItsCachedValues() {
        int[] lookups = {0};
        var variables = new BiomeVariables(null, biome -> {
            lookups[0]++;
            return Biome.Precipitation.RAIN;
        });
        var plains = biome(true, 0.8F);

        variables.setBiome(plains, null);
        variables.getPrecipitationType();
        variables.getPrecipitationType();
        assertEquals(1, lookups[0], "cached between reads");

        variables.setBiome(plains, null);
        variables.getPrecipitationType();
        assertEquals(1, lookups[0], "setting the same biome again keeps the cache");

        variables.setBiome(biome(true, 0.0F), null);
        variables.getPrecipitationType();
        assertEquals(2, lookups[0], "a different biome recomputes");
    }
}

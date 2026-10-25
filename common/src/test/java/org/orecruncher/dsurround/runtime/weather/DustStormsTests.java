package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for which biomes storms bring dust to, and when dust storms are on.
 */
public class DustStormsTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void desertsAndBadlandsAreDusty() {
        assertTrue(DustStorms.isDusty(BiomeTraits.of(BiomeTrait.DESERT, BiomeTrait.HOT), 0F));
        assertTrue(DustStorms.isDusty(BiomeTraits.of(BiomeTrait.BADLANDS), 0F));
        assertTrue(DustStorms.isDusty(BiomeTraits.of(BiomeTrait.DRY, BiomeTrait.SANDY), 0F));
    }

    @Test
    void otherDryOrSandyBiomesAreNot() {
        // A savanna is dry but grassy; a beach sandy but rained on
        assertFalse(DustStorms.isDusty(BiomeTraits.of(BiomeTrait.DRY, BiomeTrait.SAVANNA), 0F));
        assertFalse(DustStorms.isDusty(BiomeTraits.of(BiomeTrait.SANDY, BiomeTrait.BEACH), 0F));
        assertFalse(DustStorms.isDusty(BiomeTraits.of(BiomeTrait.FOREST), 0F));
    }

    @Test
    void wastelandsWithNextToNoDownfallAreDusty() {
        // Biomes O' Plenty's wasteland and wasteland steppe: no downfall, and not sandy
        var wasteland = BiomeTraits.of(BiomeTrait.DRY, BiomeTrait.HOT, BiomeTrait.OVERWORLD, BiomeTrait.SPARSE_VEGETATION, BiomeTrait.WASTELAND);
        assertTrue(DustStorms.isDusty(wasteland, 0F));
        // Its volcanic plains have the same traits, but are ash and basalt, with some downfall
        assertFalse(DustStorms.isDusty(wasteland, 0.3F));
        // Cold, snowy or swampy wastelands, and those of the Nether and the End, are not
        for (var other : new BiomeTrait[]{BiomeTrait.COLD, BiomeTrait.SNOWY, BiomeTrait.SWAMP, BiomeTrait.NETHER, BiomeTrait.END})
            assertFalse(DustStorms.isDusty(BiomeTraits.of(BiomeTrait.WASTELAND, other), 0F), other.name());
    }

    @Test
    void dustOnlyWhereNothingFallsNow() {
        // Regression: Biomes O' Plenty's lush desert is a desert that has rain, which Serene Seasons stops in the
        // tropical dry season. The rain check went by the season and the dust check by the biome's own setting, so
        // there was neither rain nor dust.
        var lushDesert = BiomeTraits.of(BiomeTrait.DESERT, BiomeTrait.DRY, BiomeTrait.LUSH, BiomeTrait.SANDY);
        assertTrue(DustStorms.isDusty(Biome.Precipitation.NONE, lushDesert, 0F), "the dry season: dust");
        assertFalse(DustStorms.isDusty(Biome.Precipitation.RAIN, lushDesert, 0F), "the wet season: rain, not dust");
        assertFalse(DustStorms.isDusty(Biome.Precipitation.SNOW, BiomeTraits.of(BiomeTrait.DESERT), 0F), "snow, not dust");
        assertFalse(DustStorms.isDusty(Biome.Precipitation.NONE, BiomeTraits.of(BiomeTrait.FOREST), 0F), "nothing, not a desert");
    }

    @Test
    void badlandsDustIsRedSand() {
        assertEquals(DustStorms.RED_SAND_COLOR, DustStorms.dustColor(BiomeTraits.of(BiomeTrait.BADLANDS)));
        assertEquals(DustStorms.SAND_COLOR, DustStorms.dustColor(BiomeTraits.of(BiomeTrait.DESERT)));
        assertEquals(DustStorms.SAND_COLOR, DustStorms.dustColor(BiomeTraits.of(BiomeTrait.DRY, BiomeTrait.SANDY)));
    }

    @Test
    void dustStormsStandAsideForTheParticleRainMod() {
        var options = new Configuration.WeatherOptions();
        options.enableParticlePrecipitation = true;
        options.enableDustStorms = true;
        assertTrue(DustStorms.enabled(options, false));
        assertFalse(DustStorms.enabled(options, true));
    }

    @Test
    void dustStormsNeedParticleRainOn() {
        var options = new Configuration.WeatherOptions();
        options.enableDustStorms = true;
        options.enableParticlePrecipitation = false;
        assertFalse(DustStorms.enabled(options, false));

        options.enableParticlePrecipitation = true;
        assertTrue(DustStorms.enabled(options, false));

        options.enableDustStorms = false;
        assertFalse(DustStorms.enabled(options, false));
    }
}

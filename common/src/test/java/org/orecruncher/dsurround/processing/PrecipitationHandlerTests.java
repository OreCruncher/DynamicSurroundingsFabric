package org.orecruncher.dsurround.processing;

import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import net.minecraft.client.ParticleStatus;
import org.orecruncher.dsurround.effects.precipitation.PrecipitationKind;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for where particle rain's splashes go.
 */
public class PrecipitationHandlerTests {

    // A water source block's surface
    private static final float SOURCE_WATER = 8F / 9F;

    @Test
    void onWaterTheSplashIsAtItsSurface() {
        // Regression: water has no collision shape, which was taken as a full block, putting the splash in the air
        // above the water, where the water ripple didn't replace it
        var top = PrecipitationHandler.surfaceTop(Double.NEGATIVE_INFINITY, SOURCE_WATER);
        assertEquals(SOURCE_WATER, top, 1e-6);
        assertTrue(top < 1D, "the splash must be inside the water's block");
    }

    @Test
    void onBlocksTheSplashIsOnTheirShape() {
        assertEquals(1D, PrecipitationHandler.surfaceTop(1D, 0F));
        assertEquals(0.5D, PrecipitationHandler.surfaceTop(0.5D, 0F), "a bottom slab");
        // A waterlogged bottom slab: the water is higher
        assertEquals(SOURCE_WATER, PrecipitationHandler.surfaceTop(0.5D, SOURCE_WATER), 1e-6);
    }

    @Test
    void theBiomesPrecipitationDecidesRainOrSnow() {
        assertEquals(PrecipitationKind.RAIN, PrecipitationHandler.kindOf(Biome.Precipitation.RAIN, false));
        assertEquals(PrecipitationKind.SNOW, PrecipitationHandler.kindOf(Biome.Precipitation.SNOW, false));
        assertEquals(PrecipitationKind.NONE, PrecipitationHandler.kindOf(Biome.Precipitation.NONE, false));
        assertEquals(PrecipitationKind.DUST, PrecipitationHandler.kindOf(Biome.Precipitation.NONE, true));
        // Dust is only where nothing falls
        assertEquals(PrecipitationKind.RAIN, PrecipitationHandler.kindOf(Biome.Precipitation.RAIN, true));
    }

    @Test
    void particleRainStandsAsideForTheParticleRainMod() {
        assertTrue(PrecipitationHandler.isEnabled(true, false));
        assertFalse(PrecipitationHandler.isEnabled(true, true), "the Particle Rain mod draws the rain");
        assertFalse(PrecipitationHandler.isEnabled(false, false), "turned off");
        assertFalse(PrecipitationHandler.isEnabled(false, true));
    }

    @Test
    void justAboveTheSnowLineSomeRainIsSnow() {
        float line = ISeasonalInformation.SNOW_TEMPERATURE;
        assertEquals(PrecipitationHandler.SLEET_MAX_SHARE, PrecipitationHandler.sleetShare(line), 1e-6F);
        assertEquals(PrecipitationHandler.SLEET_MAX_SHARE / 2F, PrecipitationHandler.sleetShare(line + PrecipitationHandler.SLEET_BAND / 2F), 1e-5F);
        assertEquals(0F, PrecipitationHandler.sleetShare(line + PrecipitationHandler.SLEET_BAND), 1e-6F);
        assertEquals(0F, PrecipitationHandler.sleetShare(0.8F), "warm: all rain");
        assertEquals(0F, PrecipitationHandler.sleetShare(line - 0.01F), "below the line it is all snow, which isn't asked");
    }

    @Test
    void densityFollowsTheOptionAndTheParticleSetting() {
        assertEquals(1F, PrecipitationHandler.density(100, ParticleStatus.ALL));
        assertEquals(0.5F, PrecipitationHandler.density(50, ParticleStatus.ALL));
        assertEquals(0.5F, PrecipitationHandler.density(100, ParticleStatus.DECREASED));
        assertEquals(0.25F, PrecipitationHandler.density(100, ParticleStatus.MINIMAL));
        assertEquals(0.125F, PrecipitationHandler.density(50, ParticleStatus.MINIMAL), 1e-6F);
    }

    @Test
    void aWiderRangeHoldsDropsForItsArea() {
        assertEquals(PrecipitationHandler.MAX_DROPS, PrecipitationHandler.capacityFor(12), "the default range");
        assertEquals(PrecipitationHandler.MAX_DROPS * 4, PrecipitationHandler.capacityFor(24), "twice as far, four times the area");
        assertEquals(Math.round(PrecipitationHandler.MAX_DROPS * (8F / 12F) * (8F / 12F)), PrecipitationHandler.capacityFor(8));
    }

    @Test
    void theSmallestDrizzleDropsDontSplash() {
        assertEquals(0F, PrecipitationHandler.splashChance(0F));
        assertEquals(0F, PrecipitationHandler.splashChance(PrecipitationHandler.NO_SPLASH_BELOW));
        assertEquals(1F, PrecipitationHandler.splashChance(PrecipitationHandler.ALWAYS_SPLASH_FROM));
        assertEquals(1F, PrecipitationHandler.splashChance(1F));

        // Rising steadily in between, with no jump
        var previous = 0F;
        for (float size = PrecipitationHandler.NO_SPLASH_BELOW; size <= PrecipitationHandler.ALWAYS_SPLASH_FROM; size += 0.01F) {
            var chance = PrecipitationHandler.splashChance(size);
            assertTrue(chance >= previous && chance - previous < 0.05F);
            previous = chance;
        }
    }

    @Test
    void withNeitherShapeNorFluidItIsTheTopOfTheBlock() {
        assertEquals(1D, PrecipitationHandler.surfaceTop(Double.NEGATIVE_INFINITY, 0F));
    }
}

package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for what falls in a column of the level: rain, snow, sleet or dust.
 */
public class LevelColumnsTests {

    @Test
    void theBiomesPrecipitationDecidesRainOrSnow() {
        assertEquals(PrecipitationKind.RAIN, LevelColumns.kindOf(Biome.Precipitation.RAIN, false));
        assertEquals(PrecipitationKind.SNOW, LevelColumns.kindOf(Biome.Precipitation.SNOW, false));
        assertEquals(PrecipitationKind.NONE, LevelColumns.kindOf(Biome.Precipitation.NONE, false));
        assertEquals(PrecipitationKind.DUST, LevelColumns.kindOf(Biome.Precipitation.NONE, true));
        // Dust is only where nothing falls
        assertEquals(PrecipitationKind.RAIN, LevelColumns.kindOf(Biome.Precipitation.RAIN, true));
    }

    @Test
    void justAboveTheSnowLineSomeRainIsSnow() {
        float line = ISeasonalInformation.SNOW_TEMPERATURE;
        assertEquals(LevelColumns.SLEET_MAX_SHARE, LevelColumns.sleetShare(line), 1e-6F);
        assertEquals(LevelColumns.SLEET_MAX_SHARE / 2F, LevelColumns.sleetShare(line + LevelColumns.SLEET_BAND / 2F), 1e-5F);
        assertEquals(0F, LevelColumns.sleetShare(line + LevelColumns.SLEET_BAND), 1e-6F);
        assertEquals(0F, LevelColumns.sleetShare(0.8F), "warm: all rain");
        assertEquals(0F, LevelColumns.sleetShare(line - 0.01F), "below the line it is all snow, which isn't asked");
    }
}

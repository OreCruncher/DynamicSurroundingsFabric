package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for what falls in a column of the level (rain, snow, sleet or dust), and where it stops.
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
    @Test
    void aSolidBlockAboveTheHeightmapStopsTheRain() {
        // The heightmap says 64; a block the heightmap left out sits at 64, with grass on it at 65
        int ground = 64, surface = 66;
        assertEquals(65, LevelColumns.topOfSolid(ground, surface, y -> y == 64), "stops on top of the block");
        // Nothing solid above the heightmap: the heightmap stands
        assertEquals(64, LevelColumns.topOfSolid(ground, surface, y -> false));
        // The highest of two stacked
        assertEquals(66, LevelColumns.topOfSolid(ground, 67, y -> y == 64 || y == 65));
    }

    @Test
    void theLookDownIsLimited() {
        // Vines hanging far down a cliff: a solid block below the look down's reach isn't found
        int ground = 10, surface = 60;
        int deep = surface - LevelColumns.MAX_LOOK_DOWN - 1;
        assertEquals(ground, LevelColumns.topOfSolid(ground, surface, y -> y == deep));
        int near = surface - LevelColumns.MAX_LOOK_DOWN;
        assertEquals(near + 1, LevelColumns.topOfSolid(ground, surface, y -> y == near));
        // And never below the heightmap
        assertEquals(64, LevelColumns.topOfSolid(64, 66, y -> y == 63));
    }

    @Test
    void solidIsJudgedAsMinecraftJudgesIt() {
        assertTrue(LevelColumns.isSolid(Shapes.block()), "a full block");
        assertTrue(LevelColumns.isSolid(Shapes.box(0.0625, 0, 0.0625, 0.9375, 0.9375, 0.9375)), "a machine a little smaller than a block");
        assertTrue(LevelColumns.isSolid(Shapes.box(0.375, 0, 0.375, 0.625, 1, 0.625)), "a post a full block tall");
        assertFalse(LevelColumns.isSolid(Shapes.empty()), "nothing to stop it");
        assertFalse(LevelColumns.isSolid(Shapes.box(0.4375, 0, 0.4375, 0.5625, 0.5, 0.5625)), "a thin short stalk");
    }
}

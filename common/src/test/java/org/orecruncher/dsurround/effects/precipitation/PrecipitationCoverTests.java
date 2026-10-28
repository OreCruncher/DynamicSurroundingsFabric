package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.runtime.weather.Wind;
import org.orecruncher.dsurround.runtime.weather.WindAccess;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for drops carried in under an overhang: they drift on in a little way, out of the wind, fading, and land
 * where they reach something, rather than vanishing at the roof's edge.
 */
public class PrecipitationCoverTests {

    private static final int GROUND = 64;
    private static final double CAMERA_Y = 70.5;
    // A roof a block thick over the downwind half of the world, with open air under it down to the ground
    private static final int ROOF_BOTTOM = 72;
    private static final int ROOF_TOP = 73;
    private static final int SAND = 0xDBD3A0;

    /**
     * Flat ground, with a roof over every column downwind of the camera
     */
    private record Overhang(PrecipitationKind kind, float windX, float windZ) implements IColumnSource {

        boolean roofed(int x, int z) {
            return (x + 0.5F) * this.windX + (z + 0.5F) * this.windZ > 0F;
        }

        @Override
        public int groundHeight(int x, int z) {
            return this.roofed(x, z) ? ROOF_TOP : GROUND;
        }

        @Override
        public boolean isOpen(int x, int y, int z) {
            if (y < GROUND)
                return false;
            return !this.roofed(x, z) || y < ROOF_BOTTOM || y >= ROOF_TOP;
        }

        @Override
        public PrecipitationKind precipitation(int x, int y, int z) {
            return this.kind;
        }

        @Override
        public int light(int x, int y, int z) {
            return this.roofed(x, z) && y < ROOF_BOTTOM ? 0x700070 : 0xF000F0;
        }

        @Override
        public int dustColor(int x, int y, int z) {
            return SAND;
        }

        @Override
        public int fallbackDustColor(int x, int y, int z) {
            return SAND;
        }
    }

    private static Wind steadyWind() {
        var wind = new Wind();
        var random = Randomizer.create(9);
        for (int i = 0; i < 300; i++)
            wind.tick(1F, false, false, 4, random);
        WindAccess.gust(wind, 0, 0F);
        return wind;
    }

    private static Overhang overhang(PrecipitationKind kind, Wind wind) {
        return new Overhang(kind, wind.directionX(), wind.directionZ());
    }

    /**
     * Ticks the field, checking every drop under cover each tick
     *
     * @param lasts how long it may stay under cover
     * @return how many drop-ticks were spent under cover
     */
    private static int run(PrecipitationField field, Overhang roof, Wind wind, int ticks, int lasts) {
        var random = Randomizer.create(42);
        int coveredTicks = 0;
        for (int t = 0; t < ticks; t++) {
            field.tick(roof, 0, CAMERA_Y, 0, 1F, 1F, wind, random);
            for (int i = 0; i < field.count(); i++) {
                if (field.drops().covered[i] == 0)
                    continue;
                coveredTicks++;
                int x = Mth.floor(field.drops().x[i]), z = Mth.floor(field.drops().z[i]);
                assertTrue(roof.roofed(x, z), "under cover but not under the roof");
                assertTrue(field.drops().y[i] < ROOF_BOTTOM, "under cover but above the roof's underside");
                assertTrue(field.drops().covered[i] <= lasts, "lasted too long under cover");
                // Out of the wind it drifts only a little way in from the edge
                float in = field.drops().x[i] * roof.windX() + field.drops().z[i] * roof.windZ();
                assertTrue(in < 6F, "drifted too far in under the roof: " + in);
                assertEquals(0x700070, field.drops().light[i], "lit as the roof's top, not where it is");
            }
        }
        return coveredTicks;
    }

    @Test
    void snowDriftsInUnderTheEavesAndFades() {
        var wind = steadyWind();
        var field = new PrecipitationField(2000);
        int covered = run(field, overhang(PrecipitationKind.SNOW, wind), wind, 200, PrecipitationField.SNOW_COVER_TICKS);
        assertTrue(covered > 0, "snow should drift in under the roof");
    }

    @Test
    void rainBlowsInOnlyBriefly() {
        var wind = steadyWind();
        var field = new PrecipitationField(2000);
        int covered = run(field, overhang(PrecipitationKind.RAIN, wind), wind, 100, PrecipitationField.RAIN_COVER_TICKS);
        assertTrue(covered > 0, "rain should blow in under the roof");
    }

    @Test
    void dustDriftsInUnderTheEavesToo() {
        var wind = steadyWind();
        var field = new PrecipitationField(2000);
        int covered = run(field, overhang(PrecipitationKind.DUST, wind), wind, 200, PrecipitationField.DUST_COVER_TICKS);
        assertTrue(covered > 0, "dust should drift in under the roof");
    }

    @Test
    void withoutWindRainDoesNotGetUnderTheRoof() {
        var wind = steadyWind();
        var roof = overhang(PrecipitationKind.RAIN, wind);
        var field = new PrecipitationField(2000);
        var random = Randomizer.create(42);
        for (int t = 0; t < 100; t++) {
            field.tick(roof, 0, CAMERA_Y, 0, 1F, 1F, new Wind(), random);
            for (int i = 0; i < field.count(); i++)
                assertEquals(0, field.drops().covered[i]);
        }
    }

    @Test
    void snowThatReachesTheGroundUnderCoverLandsThere() {
        var wind = steadyWind();
        var roof = overhang(PrecipitationKind.SNOW, wind);
        var field = new PrecipitationField(2000);
        var random = Randomizer.create(42);
        int underCover = 0;
        for (int t = 0; t < 300; t++) {
            field.tick(roof, 0, CAMERA_Y, 0, 1F, 1F, wind, random);
            for (int i = 0; i < field.landings(); i++) {
                if (!field.landingBlownIn(i))
                    continue;
                int x = Mth.floor(field.landingX(i)), z = Mth.floor(field.landingZ(i));
                // Followed on from where it was, under the roof, near the ground and coming down onto it
                if (roof.roofed(x, z) && field.landingY(i) < GROUND + 1) {
                    assertTrue(field.landingMotionY(i) < 0F);
                    underCover++;
                }
            }
        }
        assertTrue(underCover > 0, "snow drifting in under the roof should land on the ground beneath it");
    }

    @Test
    void underCoverADropFadesOut() {
        assertEquals(1F, PrecipitationField.coverFade(0F, 40));
        assertEquals(0.5F, PrecipitationField.coverFade(20F, 40), 1.0E-6F);
        assertEquals(0F, PrecipitationField.coverFade(40F, 40));
        assertEquals(0F, PrecipitationField.coverFade(60F, 40));
    }

    @Test
    void snowAndDustLingerLongerThanRain() {
        assertTrue(PrecipitationField.coverTicks(PrecipitationField.SNOW) > PrecipitationField.coverTicks(PrecipitationField.RAIN));
        assertTrue(PrecipitationField.coverTicks(PrecipitationField.DUST) > PrecipitationField.coverTicks(PrecipitationField.RAIN));
    }
}

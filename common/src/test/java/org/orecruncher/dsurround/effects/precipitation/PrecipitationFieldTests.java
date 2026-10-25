package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.runtime.weather.Wind;
import org.orecruncher.dsurround.runtime.weather.WindAccess;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the drops of particle rain and snow: how many there are, where they fall, and when they go.
 */
public class PrecipitationFieldTests {

    private static final int GROUND = 64;
    private static final double CAMERA_Y = 70.5;

    /**
     * Flat ground everywhere. Rain falls on x below {@code snowFrom}, snow from there on; nothing falls on z below
     * {@code dryBelowZ}.
     */
    private record Columns(int ground, int snowFrom, int dryBelowZ) implements IColumnSource {
        @Override
        public int groundHeight(int x, int z) {
            return this.ground;
        }

        @Override
        public PrecipitationKind precipitation(int x, int y, int z) {
            if (z < this.dryBelowZ)
                return PrecipitationKind.NONE;
            return x < this.snowFrom ? PrecipitationKind.RAIN : PrecipitationKind.SNOW;
        }

        @Override
        public int light(int x, int y, int z) {
            return 0xF000F0;
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

    private static final int SAND = 0xDBD3A0;

    /**
     * Flat ground everywhere: a desert, with dust, for x below {@code dustBelowX}, and rain from there on; or, if
     * {@code east}, the other way round
     */
    private record Desert(int ground, int dustBelowX, boolean east) implements IColumnSource {
        Desert(int ground, int dustBelowX) {
            this(ground, dustBelowX, false);
        }

        @Override
        public int groundHeight(int x, int z) {
            return this.ground;
        }

        @Override
        public PrecipitationKind precipitation(int x, int y, int z) {
            return (x < this.dustBelowX) != this.east ? PrecipitationKind.DUST : PrecipitationKind.RAIN;
        }

        @Override
        public int light(int x, int y, int z) {
            return 0xF000F0;
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

    private static final IColumnSource ALL_DESERT = new Desert(GROUND, Integer.MAX_VALUE);

    private static final IColumnSource RAIN_EVERYWHERE = new Columns(GROUND, Integer.MAX_VALUE, Integer.MIN_VALUE);

    private static void tick(PrecipitationField field, IColumnSource columns, double x, double z, float rainLevel, int ticks) {
        tick(field, columns, x, z, rainLevel, new Wind(), ticks);
    }

    private static void tick(PrecipitationField field, IColumnSource columns, double x, double z, float rainLevel, Wind wind, int ticks) {
        var random = Randomizer.create(42);
        for (int i = 0; i < ticks; i++)
            field.tick(columns, x, CAMERA_Y, z, rainLevel, 1F, wind, random);
    }

    /**
     * A wind at full storm strength, blowing steadily, without gusts
     */
    private static Wind steadyWind() {
        var wind = new Wind();
        var random = Randomizer.create(9);
        // Long enough to build up most of the way; then no gust is under way
        for (int i = 0; i < 300; i++) {
            wind.tick(1F, false, false, 4, random);
        }
        WindAccess.gust(wind, 0, 0F);
        return wind;
    }

    @Test
    void theNumberOfDropsFollowsTheRainLevel() {
        var field = new PrecipitationField(1000);
        tick(field, RAIN_EVERYWHERE, 0, 0, 0.5F, 20);
        assertTrue(field.count() >= 450 && field.count() <= 500, () -> "half strength gave " + field.count());

        tick(field, RAIN_EVERYWHERE, 0, 0, 1F, 20);
        assertTrue(field.count() >= 950, () -> "full strength gave " + field.count());
        assertTrue(field.count() <= field.capacity());
    }

    @Test
    void whenTheRainStopsTheDropsFallOut() {
        var field = new PrecipitationField(1000);
        tick(field, RAIN_EVERYWHERE, 0, 0, 1F, 10);
        assertTrue(field.count() > 0);

        // Rain falls at least RAIN_SPEED_MIN a tick through the cylinder's height
        int ticks = (int) Math.ceil((PrecipitationField.ABOVE + PrecipitationField.BELOW) / PrecipitationField.RAIN_SPEED_MIN) + 1;
        tick(field, RAIN_EVERYWHERE, 0, 0, 0F, ticks);
        assertEquals(0, field.count());
    }

    @Test
    void dropsStayAboveTheGroundAndNearTheCamera() {
        var field = new PrecipitationField(1000);
        tick(field, RAIN_EVERYWHERE, 0, 0, 1F, 30);
        float radius = PrecipitationField.DEFAULT_RADIUS;
        for (int i = 0; i < field.count(); i++) {
            assertTrue(field.y[i] > GROUND, "below the ground");
            assertTrue(field.y[i] <= CAMERA_Y + PrecipitationField.ABOVE, "above the cylinder");
            assertTrue(field.x[i] * field.x[i] + field.z[i] * field.z[i] <= radius * radius, "outside the cylinder");
        }
    }

    @Test
    void nothingFallsWhereTheBiomeIsDry() {
        var field = new PrecipitationField(1000);
        // Dry for z below 0: half the disc
        tick(field, new Columns(GROUND, Integer.MAX_VALUE, 0), 0, 0, 1F, 20);
        assertTrue(field.count() > 0);
        for (int i = 0; i < field.count(); i++)
            assertTrue(field.z[i] >= 0, "a drop in a dry column");
    }

    @Test
    void groundAboveTheCylinderHasNoDrops() {
        var field = new PrecipitationField(1000);
        tick(field, new Columns((int) CAMERA_Y + PrecipitationField.ABOVE + 1, Integer.MAX_VALUE, Integer.MIN_VALUE), 0, 0, 1F, 10);
        assertEquals(0, field.count());
    }

    @Test
    void eachFlakeAndPuffKeepsTheTurnOfItsPhase() {
        // The turn is worked out as a drop ticks, not as it is drawn; it must follow the phase through spawning,
        // ticking, and drops being moved about in the arrays as others go. Snow, rain and dust, enough ticks for
        // plenty to land or settle and be replaced
        var snow = new PrecipitationField(1000);
        tick(snow, new Columns(GROUND, 0, Integer.MIN_VALUE), 0, 0, 1F, 120);
        var dust = new PrecipitationField(1000);
        tick(dust, new Desert(GROUND, 0), 0, 0, 1F, 120);
        int checked = 0;
        for (var field : new PrecipitationField[]{snow, dust}) {
            for (int i = 0; i < field.count(); i++) {
                if (field.kind[i] == PrecipitationField.RAIN)
                    continue;
                float spin = field.phase[i] * (field.kind[i] == PrecipitationField.DUST
                        ? PrecipitationRenderer.DUST_SPIN : PrecipitationRenderer.SNOW_SPIN);
                assertEquals(Mth.cos(spin), field.spinCos[i], 1e-6F, "drop " + i);
                assertEquals(Mth.sin(spin), field.spinSin[i], 1e-6F, "drop " + i);
                checked++;
            }
        }
        assertTrue(snow.dustCount() == 0 && dust.dustCount() > 0);
        assertTrue(checked > 100, checked + " checked");
    }

    @Test
    void coldColumnsGetSlowSnow() {
        var field = new PrecipitationField(1000);
        // Snow for x from 0 on
        tick(field, new Columns(GROUND, 0, Integer.MIN_VALUE), 0, 0, 1F, 10);
        boolean sawRain = false, sawSnow = false;
        for (int i = 0; i < field.count(); i++) {
            if (field.kind[i] == PrecipitationField.SNOW) {
                sawSnow = true;
                assertTrue(field.speed[i] < PrecipitationField.RAIN_SPEED_MIN);
            } else {
                sawRain = true;
            }
        }
        assertTrue(sawRain && sawSnow);
    }

    @Test
    void dropsLeftBehindByTheCameraGo() {
        var field = new PrecipitationField(1000);
        tick(field, RAIN_EVERYWHERE, 0, 0, 1F, 10);

        // Far enough that none of the old drops are near
        tick(field, RAIN_EVERYWHERE, 100, 100, 1F, 1);
        float radius = PrecipitationField.DEFAULT_RADIUS;
        for (int i = 0; i < field.count(); i++) {
            float dx = field.x[i] - 100, dz = field.z[i] - 100;
            assertTrue(dx * dx + dz * dz <= radius * radius, "a drop left behind");
        }
    }

    @Test
    void aWiderFieldKeepsLandingsForItsArea() {
        // Regression: at a wide range only the first 128 landings a tick splashed, so splashes thinned out
        assertEquals(PrecipitationField.MAX_LANDINGS, new PrecipitationField(1000).maxLandings(), "the default range");
        assertEquals(PrecipitationField.MAX_LANDINGS * 4, new PrecipitationField(1000, PrecipitationField.DEFAULT_RADIUS * 2).maxLandings());
        assertTrue(new PrecipitationField(1000, 8).maxLandings() < PrecipitationField.MAX_LANDINGS, "narrower, fewer");
    }

    @Test
    void numbersScaleWithTheArea() {
        assertEquals(16, PrecipitationField.forArea(16, PrecipitationField.DEFAULT_RADIUS));
        assertEquals(64, PrecipitationField.forArea(16, PrecipitationField.DEFAULT_RADIUS * 2));
        assertEquals(Math.round(16 * (32F / 12F) * (32F / 12F)), PrecipitationField.forArea(16, 32));
        assertEquals(1, PrecipitationField.forArea(1, 1), "never none");
    }

    @Test
    void rainDropsLandOnTheGround() {
        var field = new PrecipitationField(1000);
        // Long enough for drops to fall from the top of the cylinder to the ground
        tick(field, RAIN_EVERYWHERE, 0, 0, 1F, 30);
        assertTrue(field.landings() > 0);
        assertTrue(field.landings() <= field.maxLandings());
        float radius = PrecipitationField.DEFAULT_RADIUS;
        for (int i = 0; i < field.landings(); i++) {
            assertEquals(GROUND, field.landingGround(i));
            float x = field.landingX(i), z = field.landingZ(i);
            assertTrue(x * x + z * z <= radius * radius, "landed outside the cylinder");
        }
    }

    @Test
    void snowLandsAsSnowForTheFlecks() {
        var field = new PrecipitationField(1000);
        // Snow everywhere, on ground just below the camera so flakes reach it quickly
        tick(field, new Columns((int) CAMERA_Y - 1, Integer.MIN_VALUE, Integer.MIN_VALUE), 0, 0, 1F, 200);
        assertTrue(field.count() > 0);
        assertTrue(field.landings() > 0);
        for (int i = 0; i < field.landings(); i++) {
            assertTrue(field.landingIsSnow(i));
            assertTrue(field.landingSize(i) >= 0F && field.landingSize(i) <= 1F);
            assertTrue(field.landingVariant(i) >= 0F && field.landingVariant(i) <= 1F);
        }
    }

    @Test
    void snowCarriedIntoTheSideOfSomethingTallerHitsTheSideNotTheTop() {
        // Every other column a pillar topping out above the cylinder, so no drop starts over one: any landing on a
        // pillar's top would be a flake that swayed into its side
        int low = (int) CAMERA_Y - 1, tall = (int) CAMERA_Y + PrecipitationField.ABOVE + 10;
        var pillars = new IColumnSource() {
            @Override
            public int groundHeight(int x, int z) {
                return (x & 1) == 0 ? low : tall;
            }

            @Override
            public PrecipitationKind precipitation(int x, int y, int z) {
                return PrecipitationKind.SNOW;
            }

            @Override
            public int light(int x, int y, int z) {
                return 0xF000F0;
            }

            @Override
            public int dustColor(int x, int y, int z) {
                return SAND;
            }

            @Override
            public int fallbackDustColor(int x, int y, int z) {
                return SAND;
            }
        };
        var field = new PrecipitationField(1000);
        var random = Randomizer.create(42);
        int landedOnLow = 0, hitSides = 0;
        for (int t = 0; t < 200; t++) {
            field.tick(pillars, 0, CAMERA_Y, 0, 1F, 1F, new Wind(), random);
            for (int i = 0; i < field.landings(); i++) {
                if (!field.landingBlownIn(i)) {
                    assertEquals(low, field.landingGround(i), "landed on top of a pillar it hit the side of");
                    landedOnLow++;
                    continue;
                }
                hitSides++;
                // Followed on from the gap it was in, moving into a pillar's column, below the pillar's top
                float x = field.landingX(i);
                assertEquals(low, pillars.groundHeight(Mth.floor(x), 0), "should start out in the open");
                assertEquals(tall, pillars.groundHeight(Mth.floor(x + field.landingMotionX(i)), 0), "should be moving into a pillar");
                assertTrue(field.landingY(i) <= tall);
            }
        }
        assertTrue(landedOnLow > 0, "flakes should still land between the pillars");
        assertTrue(hitSides > 0, "swaying flakes should hit the pillars' sides");
    }

    @Test
    void rainBlownIntoTheSideOfSomethingIsFollowedOnForTheWindows() {
        // A checkerboard of pillars topping out above the cylinder: in any wind, rain crosses into them from the side
        int low = (int) CAMERA_Y - 1, tall = (int) CAMERA_Y + PrecipitationField.ABOVE + 10;
        var pillars = new IColumnSource() {
            @Override
            public int groundHeight(int x, int z) {
                return ((x ^ z) & 1) == 0 ? low : tall;
            }

            @Override
            public PrecipitationKind precipitation(int x, int y, int z) {
                return PrecipitationKind.RAIN;
            }

            @Override
            public int light(int x, int y, int z) {
                return 0xF000F0;
            }

            @Override
            public int dustColor(int x, int y, int z) {
                return SAND;
            }

            @Override
            public int fallbackDustColor(int x, int y, int z) {
                return SAND;
            }
        };
        var field = new PrecipitationField(1000);
        var wind = steadyWind();
        var random = Randomizer.create(42);
        int blownIn = 0;
        for (int t = 0; t < 40; t++) {
            field.tick(pillars, 0, CAMERA_Y, 0, 1F, 1F, wind, random);
            for (int i = 0; i < field.landings(); i++) {
                if (!field.landingBlownIn(i))
                    continue;
                blownIn++;
                assertFalse(field.landingIsSnow(i));
                float x = field.landingX(i), z = field.landingZ(i);
                assertEquals(low, pillars.groundHeight(Mth.floor(x), Mth.floor(z)), "should start out in the open");
                assertEquals(tall, pillars.groundHeight(Mth.floor(x + field.landingMotionX(i)), Mth.floor(z + field.landingMotionZ(i))),
                        "should be moving into a pillar");
                assertTrue(field.landingMotionY(i) < 0F, "falling");
            }
        }
        assertTrue(blownIn > 0, "wind-blown rain should hit the pillars' sides");
    }

    @Test
    void rainLandsAsRain() {
        var field = new PrecipitationField(1000);
        tick(field, RAIN_EVERYWHERE, 0, 0, 1F, 30);
        assertTrue(field.landings() > 0);
        for (int i = 0; i < field.landings(); i++)
            assertFalse(field.landingIsSnow(i));
    }

    @Test
    void dropsFallingOutOfTheBottomDontLand() {
        var field = new PrecipitationField(1000);
        // Ground far below the cylinder: drops leave through its bottom without reaching it
        tick(field, new Columns(0, Integer.MAX_VALUE, Integer.MIN_VALUE), 0, 0, 1F, 40);
        assertEquals(0, field.landings());
    }

    @Test
    void clearingForgetsTheLandings() {
        var field = new PrecipitationField(1000);
        tick(field, RAIN_EVERYWHERE, 0, 0, 1F, 30);
        field.clear();
        assertEquals(0, field.landings());
    }

    @Test
    void withoutWindRainFallsStraightDown() {
        var field = new PrecipitationField(1000);
        tick(field, RAIN_EVERYWHERE, 0, 0, 1F, 10);
        for (int i = 0; i < field.count(); i++) {
            assertEquals(0F, field.windX[i]);
            assertEquals(0F, field.windZ[i]);
        }
    }

    @Test
    void theWindCarriesDropsDownwind() {
        var wind = steadyWind();
        assertTrue(wind.speed() > 0.1F, () -> "only " + wind.speed());
        var field = new PrecipitationField(1000);
        tick(field, RAIN_EVERYWHERE, 0, 0, 1F, wind, 10);

        assertTrue(field.count() > 0);
        for (int i = 0; i < field.count(); i++) {
            // Each drop moves the way the wind blows, by about as much, give or take its own drift
            float along = field.windX[i] * wind.x() + field.windZ[i] * wind.z();
            float speedSq = wind.speed() * wind.speed();
            assertTrue(along > 0.8F * speedSq && along < 1.2F * speedSq, "a drop not carried with the wind");
        }
    }

    @Test
    void snowCatchesTheWindLaterThanRain() {
        var wind = steadyWind();
        var rain = new PrecipitationField(1000);
        var snow = new PrecipitationField(1000);
        tick(rain, RAIN_EVERYWHERE, 0, 0, 1F, wind, 5);
        tick(snow, new Columns(GROUND, Integer.MIN_VALUE, Integer.MIN_VALUE), 0, 0, 1F, wind, 5);

        // A gust doubles the wind; one tick later, rain has gone further toward it than snow has
        WindAccess.gust(wind, 40, 1F);
        for (int t = 0; t < 30; t++) {
            wind.tick(1F, false, false, 4, Randomizer.create(t));
        }
        var random = Randomizer.create(1);
        rain.tick(RAIN_EVERYWHERE, 0, CAMERA_Y, 0, 0F, 1F, wind, random);
        snow.tick(new Columns(GROUND, Integer.MIN_VALUE, Integer.MIN_VALUE), 0, CAMERA_Y, 0, 0F, 1F, wind, random);
        assertTrue(meanSpeed(rain) / averageDrift(rain) > meanSpeed(snow) / averageDrift(snow));
    }

    private static float meanSpeed(PrecipitationField field) {
        float total = 0F;
        for (int i = 0; i < field.count(); i++)
            total += Math.hypot(field.windX[i], field.windZ[i]);
        return total / field.count();
    }

    private static float averageDrift(PrecipitationField field) {
        float total = 0F;
        for (int i = 0; i < field.count(); i++)
            total += field.drift[i];
        return total / field.count();
    }

    @Test
    void snowflakesAreSpreadOverTheirLooks() {
        var field = new PrecipitationField(1000);
        tick(field, new Columns(GROUND, Integer.MIN_VALUE, Integer.MIN_VALUE), 0, 0, 1F, 5);
        var seen = new int[4];
        for (int i = 0; i < field.count(); i++)
            seen[field.variant(i, seen.length)]++;
        for (int n = 0; n < seen.length; n++) {
            int look = n;
            assertTrue(seen[n] > field.count() / 8, () -> "look " + look + " only " + seen[look] + " of " + field.count());
        }
    }

    @Test
    void snowflakesVaryInSizeMostlySmall() {
        var field = new PrecipitationField(1000);
        tick(field, new Columns(GROUND, Integer.MIN_VALUE, Integer.MIN_VALUE), 0, 0, 1F, 5);
        int small = 0, large = 0;
        for (int i = 0; i < field.count(); i++) {
            var size = field.snowSize(i);
            assertTrue(size >= 0F && size <= 1F);
            if (size < 0.25F)
                small++;
            else if (size > 0.75F)
                large++;
        }
        assertTrue(large > 0, "no large flakes");
        assertTrue(small > 2 * large, () -> "small flakes should be the most common");
    }

    @Test
    void largerSnowflakesFallFaster() {
        var field = new PrecipitationField(1000);
        tick(field, new Columns(GROUND, Integer.MIN_VALUE, Integer.MIN_VALUE), 0, 0, 1F, 5);
        for (int i = 0; i < field.count(); i++)
            for (int j = 0; j < field.count(); j++)
                if (field.speed[i] > field.speed[j])
                    assertTrue(field.snowSize(i) >= field.snowSize(j));
    }

    @Test
    void lightRainHasSmallDropsAndADownpourLargeOnes() {
        var random = Randomizer.create(21);
        float lightTotal = 0F, heavyTotal = 0F;
        for (int i = 0; i < 1000; i++) {
            lightTotal += PrecipitationField.rainDropSize(0.1F, random);
            heavyTotal += PrecipitationField.rainDropSize(1F, random);
        }
        var light = lightTotal / 1000;
        var heavy = heavyTotal / 1000;
        assertTrue(light < 0.3F, () -> "light rain averaged " + light);
        assertTrue(heavy > 0.75F, () -> "a downpour averaged " + heavy);
    }

    @Test
    void smallDropsFallSlower() {
        var light = new PrecipitationField(1000);
        var heavy = new PrecipitationField(1000);
        tick(light, RAIN_EVERYWHERE, 0, 0, 0.1F, 3);
        tick(heavy, RAIN_EVERYWHERE, 0, 0, 1F, 3);
        assertTrue(meanFallSpeed(light) < meanFallSpeed(heavy));
        for (int i = 0; i < heavy.count(); i++)
            assertEquals((heavy.speed[i] - PrecipitationField.RAIN_SPEED_MIN) / PrecipitationField.RAIN_SPEED_RANGE, heavy.rainSize(i), 1e-5F);
    }

    private static float meanFallSpeed(PrecipitationField field) {
        float total = 0F;
        for (int i = 0; i < field.count(); i++)
            total += field.speed[i];
        return total / field.count();
    }

    @Test
    void landingsCarryTheDropsSize() {
        var field = new PrecipitationField(1000);
        tick(field, RAIN_EVERYWHERE, 0, 0, 1F, 30);
        assertTrue(field.landings() > 0);
        for (int i = 0; i < field.landings(); i++)
            assertTrue(field.landingSize(i) >= 0F && field.landingSize(i) <= 1F);
    }

    @Test
    void aDesertFillsWithDustNotRain() {
        var field = new PrecipitationField(1000);
        tick(field, ALL_DESERT, 0, 0, 1F, 10);
        assertTrue(field.count() > 0);
        assertEquals(field.count(), field.dustCount(), "everything in a desert is dust");
        for (int i = 0; i < field.count(); i++) {
            assertEquals(PrecipitationField.DUST, field.kind[i]);
            assertEquals(SAND, field.color[i]);
        }
        assertTrue(field.isDustAt(0, 0));
    }

    @Test
    void dustTakesOnlyItsShareOfTheField() {
        var field = new PrecipitationField(1000);
        tick(field, ALL_DESERT, 0, 0, 1F, 20);
        assertTrue(field.dustCount() <= (int) (field.capacity() * PrecipitationField.DUST_SHARE));
        assertTrue(field.dustCount() > (int) (field.capacity() * PrecipitationField.DUST_SHARE) / 2);

        var half = new PrecipitationField(1000);
        tick(half, ALL_DESERT, 0, 0, 0.5F, 20);
        assertTrue(half.dustCount() <= (int) (half.capacity() * PrecipitationField.DUST_SHARE * 0.5F));
    }

    @Test
    void dustBlowsLowAboveTheGround() {
        var field = new PrecipitationField(1000);
        tick(field, ALL_DESERT, 0, 0, 1F, 20);
        int low = 0;
        for (int i = 0; i < field.count(); i++) {
            // The turbulence can dip a puff a little below its usual lift, from which it eases back up; never into
            // the ground
            assertTrue(field.y[i] > GROUND, "dust below the ground");
            if (field.y[i] < GROUND + PrecipitationField.DUST_MIN_LIFT + PrecipitationField.DUST_HEIGHT / 2F)
                low++;
        }
        // Squaring the height puts about seven in ten in the lower half
        assertTrue(low > field.count() * 0.6F, () -> "too few puffs low down");
    }

    @Test
    void dustSettlesAndNeverLands() {
        var field = new PrecipitationField(1000);
        tick(field, ALL_DESERT, 0, 0, 1F, 10);
        assertTrue(field.count() > 0);
        assertEquals(0, field.landings());

        // With the storm over, every puff settles within its lifetime
        tick(field, ALL_DESERT, 0, 0, 0F, PrecipitationField.DUST_MIN_LIFETIME + PrecipitationField.DUST_LIFETIME_RANGE);
        assertEquals(0, field.count());
        assertEquals(0, field.dustCount());
    }

    @Test
    void dustFadesInAndOut() {
        var field = new PrecipitationField(100);
        tick(field, ALL_DESERT, 0, 0, 1F, 1);
        assertTrue(field.count() > 0);
        assertEquals(0F, field.fade(0, 0F));
        // Half way through its life it is at full strength; at the end, gone
        field.age[0] = field.lifetime[0] / 2;
        assertEquals(1F, field.fade(0, 0F));
        field.age[0] = field.lifetime[0];
        assertEquals(0F, field.fade(0, 0F));
    }

    @Test
    void dustBlownOutOfTheDesertFadesOut() {
        // Desert for x from 0, rain to the west, where the wind blows: dust blown out of the desert drifts on a little
        // way, fading, then goes
        var wind = steadyWind();
        var field = new PrecipitationField(1000);
        assertTrue(wind.x() < 0F, "the wind blows out of the desert");
        var desert = new Desert(GROUND, 0, true);
        var random = Randomizer.create(42);
        boolean sawFading = false;
        for (int t = 0; t < 60; t++) {
            field.tick(desert, 0, CAMERA_Y, 0, 1F, 1F, wind, random);
            for (int i = 0; i < field.count(); i++) {
                if (field.kind[i] != PrecipitationField.DUST || field.x[i] > -1F)
                    continue;
                assertTrue(field.lifetime[i] - field.age[i] <= PrecipitationField.DUST_FADE, "dust outside the desert not fading");
                sawFading |= field.fade(i, 0F) > 0F;
            }
        }
        assertTrue(sawFading, "no dust drifted out of the desert");
    }

    /**
     * Desert everywhere, rising in one-block steps from {@code firstStep} eastward, a block every {@code run} blocks
     */
    private record Slope(int firstStep, int run) implements IColumnSource {
        @Override
        public int groundHeight(int x, int z) {
            return GROUND + (x < this.firstStep ? 0 : 1 + (x - this.firstStep) / this.run);
        }

        @Override
        public PrecipitationKind precipitation(int x, int y, int z) {
            return PrecipitationKind.DUST;
        }

        @Override
        public int light(int x, int y, int z) {
            return 0xF000F0;
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

    /**
     * A strong wind blowing due east, toward positive x
     */
    private static Wind eastWind(float speed) {
        var wind = new Wind();
        WindAccess.setSteady(wind, 0F, speed);
        return wind;
    }

    @Test
    void dustGlidesUpASlopeRatherThanJumping() {
        // Regression: a puff reaching a higher column was put on top of it in a single tick, a jump of a block
        var columns = new Slope(-4, 2);
        var wind = eastWind(0.5F);
        var field = new PrecipitationField(1000);
        var random = Randomizer.create(31);
        // How much more a puff rose in a tick than its climb allows, from how far it was blown; the turbulence adds
        // a little either way
        float worst = Float.NEGATIVE_INFINITY;
        for (int t = 0; t < 60; t++) {
            field.tick(columns, 0, CAMERA_Y, 0, 1F, 1F, wind, random);
            for (int i = 0; i < field.count(); i++) {
                if (field.age[i] == 0)
                    continue;
                float along = (float) Math.hypot(field.windX[i], field.windZ[i]);
                float rise = field.y[i] - field.prevY[i];
                worst = Math.max(worst, rise - Math.max(along * PrecipitationField.DUST_MAX_CLIMB, PrecipitationField.DUST_MIN_RISE));
            }
        }
        float finalWorst = worst;
        float turbulence = PrecipitationField.DUST_TURBULENCE_HEIGHT * PrecipitationField.DUST_TURBULENCE_RATE;
        assertTrue(finalWorst <= turbulence + 1e-4F, () -> "a puff rose " + finalWorst + " more than its climb allows");
    }

    @Test
    void dustRidesUpASteadySlopeInAStrongWind() {
        // A one in one slope, in the strongest dust storm wind: the puffs keep up with the ground rather than being
        // left behind in it and taken for blown into a wall
        var columns = new Slope(-12, 1);
        var wind = eastWind(WindAccess.strongest());
        var field = new PrecipitationField(1000);
        var random = Randomizer.create(37);
        // Long enough that every puff now has climbed from where it spawned
        for (int t = 0; t < 30; t++)
            field.tick(columns, 0, CAMERA_Y, 0, 1F, 1F, wind, random);
        int climbing = 0, buried = 0;
        for (int i = 0; i < field.count(); i++) {
            if (field.age[i] < 10)
                continue;
            climbing++;
            float ground = columns.groundHeight((int) Math.floor(field.x[i]), 0);
            if (field.y[i] < ground)
                buried++;
        }
        assertTrue(climbing > 0);
        int finalBuried = buried, finalClimbing = climbing;
        assertTrue(buried < climbing / 10, () -> finalBuried + " of " + finalClimbing + " puffs left in the slope");
    }

    @Test
    void theEdgeOfTheRainIsAsDenseAsItsMiddle() {
        // Regression: with half the area dry, every drop the storm called for went into the other half, twice as
        // densely, and the field spent its tries every tick on the dry half
        var whole = new PrecipitationField(1000);
        var half = new PrecipitationField(1000);
        tick(whole, RAIN_EVERYWHERE, 0, 0, 1F, 20);
        // Dry for z below 0: 12 of the 25 rows
        tick(half, new Columns(GROUND, Integer.MAX_VALUE, 0), 0, 0, 1F, 20);
        float expected = whole.count() * 13F / 25F;
        assertEquals(expected, half.count(), expected * 0.1F);
    }

    @Test
    void aFieldWithNothingLeftIsIdleBetweenStorms() {
        var field = new PrecipitationField(1000);
        assertTrue(field.isIdle(0F));
        assertFalse(field.isIdle(0.5F), "a storm on");

        tick(field, RAIN_EVERYWHERE, 0, 0, 1F, 5);
        assertFalse(field.isIdle(0F), "drops still falling");
    }

    @Test
    void dropsWellBehindTheCameraAreNotDrawn() {
        // Looking north, toward negative z
        var look = new Vector3f(0F, 0F, -1F);
        var margin = PrecipitationRenderer.behindMargin(DropScales.USUAL);
        assertFalse(PrecipitationRenderer.isBehind(0F, 0F, -5F, look, margin), "ahead");
        assertFalse(PrecipitationRenderer.isBehind(4F, 0F, 0F, look, margin), "beside");
        assertFalse(PrecipitationRenderer.isBehind(0F, 0F, margin - 0.1F, look, margin), "just behind: a streak could reach into view");
        assertTrue(PrecipitationRenderer.isBehind(0F, 0F, 5F, look, margin), "well behind");
    }

    @Test
    void theMarginBehindTheCameraCoversTheLargestDropAtAnyScale() {
        for (var scales : new DropScales[]{DropScales.USUAL, new DropScales(0.5F, 0.5F), new DropScales(2.5F, 2F)}) {
            var margin = PrecipitationRenderer.behindMargin(scales);
            var dust = PrecipitationRenderer.DUST_MIN_HALF_SIZE + PrecipitationRenderer.DUST_HALF_SIZE_RANGE;
            var snow = (PrecipitationRenderer.SNOW_MIN_HALF_SIZE + PrecipitationRenderer.SNOW_HALF_SIZE_RANGE) * scales.snow();
            assertTrue(margin > PrecipitationRenderer.rainLength(1F, scales.rain()), "the longest streak, at " + scales);
            assertTrue(margin > dust, "the largest puff, at " + scales);
            assertTrue(margin > snow, "the largest flake, at " + scales);
        }
        assertTrue(PrecipitationRenderer.behindMargin(new DropScales(2.5F, 1F)) > PrecipitationRenderer.behindMargin(DropScales.USUAL),
                "grows with long streaks");
    }

    @Test
    void smallerRainIsShorterButNoThinner() {
        assertEquals(PrecipitationRenderer.rainLength(0.5F, 1F) * 0.5F, PrecipitationRenderer.rainLength(0.5F, 0.5F), 1.0E-6F);
        assertEquals(PrecipitationRenderer.rainHalfWidth(0.5F, 1F), PrecipitationRenderer.rainHalfWidth(0.5F, 0.5F), 1.0E-6F,
                "thinner would flicker");
    }

    @Test
    void largerRainWidensLessThanItLengthens() {
        var length = PrecipitationRenderer.rainLength(0.5F, 2F) / PrecipitationRenderer.rainLength(0.5F, 1F);
        var width = PrecipitationRenderer.rainHalfWidth(0.5F, 2F) / PrecipitationRenderer.rainHalfWidth(0.5F, 1F);
        assertEquals(2F, length, 1.0E-5F);
        assertTrue(width > 1F && width < length, "wider, but less: " + width);
    }

    @Test
    void distantRainAndSnowAreThinnedOut() {
        assertEquals(1F, PrecipitationRenderer.shareDrawn(0F, PrecipitationField.DEFAULT_RADIUS));
        assertEquals(1F, PrecipitationRenderer.shareDrawn(PrecipitationField.DEFAULT_RADIUS * PrecipitationRenderer.THIN_FROM_SHARE, PrecipitationField.DEFAULT_RADIUS));
        assertEquals(PrecipitationRenderer.THIN_KEEP_AT_EDGE, PrecipitationRenderer.shareDrawn(PrecipitationField.DEFAULT_RADIUS, PrecipitationField.DEFAULT_RADIUS), 1e-6F);
        float previous = 1F;
        for (float d = 0F; d <= PrecipitationField.DEFAULT_RADIUS; d += 0.25F) {
            float share = PrecipitationRenderer.shareDrawn(d, PrecipitationField.DEFAULT_RADIUS);
            assertTrue(share <= previous, "the share drawn grows with distance");
            previous = share;
        }
    }

    @Test
    void aThinnedDropFadesRatherThanPopping() {
        // Near, every drop is drawn whole
        assertEquals(1F, PrecipitationRenderer.thinned(0.99F, 1F, PrecipitationField.DEFAULT_RADIUS));
        // At the edge, drops under the share are drawn whole, those well over it not at all, and those just over it
        // part way, so a drop slips out as the camera moves away
        float edge = PrecipitationField.DEFAULT_RADIUS;
        float share = PrecipitationRenderer.THIN_KEEP_AT_EDGE;
        assertEquals(1F, PrecipitationRenderer.thinned(share - 0.01F, edge, PrecipitationField.DEFAULT_RADIUS));
        assertEquals(0F, PrecipitationRenderer.thinned(share + PrecipitationRenderer.THIN_FADE + 0.01F, edge, PrecipitationField.DEFAULT_RADIUS));
        float part = PrecipitationRenderer.thinned(share + PrecipitationRenderer.THIN_FADE / 2F, edge, PrecipitationField.DEFAULT_RADIUS);
        assertTrue(part > 0F && part < 1F, () -> "part way: " + part);
    }

    @Test
    void thinningSparesAboutTwoFifthsOfTheDrawing() {
        // Drops spread evenly over the disc, each with its own random number: how many are drawn at all
        var random = Randomizer.create(41);
        int drawn = 0, n = 20000;
        for (int i = 0; i < n; i++) {
            float distance = PrecipitationField.DEFAULT_RADIUS * (float) Math.sqrt(random.nextFloat());
            if (PrecipitationRenderer.thinned(random.nextFloat(), distance, PrecipitationField.DEFAULT_RADIUS) > 0F)
                drawn++;
        }
        float share = drawn / (float) n;
        assertTrue(share > 0.55F && share < 0.68F, () -> "drew " + share + " of the drops");
    }

    /**
     * Rain everywhere, with the given share of it falling as snow
     */
    private record Sleet(float share) implements IColumnSource {
        @Override
        public int groundHeight(int x, int z) {
            return GROUND;
        }

        @Override
        public PrecipitationKind precipitation(int x, int y, int z) {
            return PrecipitationKind.RAIN;
        }

        @Override
        public float snowShare(int x, int y, int z) {
            return this.share;
        }

        @Override
        public int light(int x, int y, int z) {
            return 0xF000F0;
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

    private static float snowShareOf(PrecipitationField field) {
        int snow = 0;
        for (int i = 0; i < field.count(); i++)
            if (field.kind[i] == PrecipitationField.SNOW)
                snow++;
        return snow / (float) field.count();
    }

    @Test
    void nearTheSnowLineTheRainIsPartSnow() {
        var none = new PrecipitationField(1000);
        tick(none, new Sleet(0F), 0, 0, 1F, 3);
        assertEquals(0F, snowShareOf(none));

        var half = new PrecipitationField(1000);
        tick(half, new Sleet(0.5F), 0, 0, 1F, 3);
        assertEquals(0.5F, snowShareOf(half), 0.08F);
    }

    @Test
    void densityThinsTheField() {
        var full = new PrecipitationField(1000);
        var half = new PrecipitationField(1000);
        var random = Randomizer.create(5);
        for (int t = 0; t < 20; t++) {
            full.tick(RAIN_EVERYWHERE, 0, CAMERA_Y, 0, 1F, 1F, new Wind(), random);
            half.tick(RAIN_EVERYWHERE, 0, CAMERA_Y, 0, 1F, 0.5F, new Wind(), random);
        }
        assertEquals(full.count() / 2F, half.count(), full.count() * 0.06F);
    }

    @Test
    void aWiderRangeHasDropsFurtherOut() {
        var wide = new PrecipitationField(4000, 20);
        tick(wide, RAIN_EVERYWHERE, 0, 0, 1F, 10);
        float furthest = 0F;
        for (int i = 0; i < wide.count(); i++) {
            float distance = (float) Math.hypot(wide.x[i], wide.z[i]);
            assertTrue(distance <= 20F + 1e-4F, "beyond the range");
            furthest = Math.max(furthest, distance);
        }
        assertTrue(furthest > PrecipitationField.DEFAULT_RADIUS, "none past the default range");
        assertEquals(20, wide.radius());
    }

    @Test
    void thinningFollowsTheRange() {
        // The same share is drawn at the same share of the way out, whatever the range
        for (float share = 0F; share <= 1F; share += 0.1F) {
            float atDefault = PrecipitationRenderer.shareDrawn(share * PrecipitationField.DEFAULT_RADIUS, PrecipitationField.DEFAULT_RADIUS);
            float atWide = PrecipitationRenderer.shareDrawn(share * 24F, 24F);
            assertEquals(atDefault, atWide, 1e-5F);
        }
        assertEquals(PrecipitationRenderer.THIN_KEEP_AT_EDGE, PrecipitationRenderer.shareDrawn(24F, 24F), 1e-6F);
    }

    @Test
    void newDropsFadeIn() {
        var field = new PrecipitationField(10);
        tick(field, RAIN_EVERYWHERE, 0, 0, 1F, 1);
        assertTrue(field.count() > 0);
        assertEquals(0F, field.fade(0, 0F));
        assertTrue(field.fade(0, 0.5F) > 0F && field.fade(0, 0.5F) < 1F);
    }
}

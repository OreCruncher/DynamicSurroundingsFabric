package org.orecruncher.dsurround.runtime.weather;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.precipitation.PrecipitationField;
import org.orecruncher.dsurround.lib.random.Randomizer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the wind that slants rain and snow: its strength, its direction, and its gusts.
 */
public class WindTests {

    private static Wind blow(float rainLevel, boolean thundering, long day, int ticks) {
        var wind = new Wind();
        var random = Randomizer.create(11);
        for (int i = 0; i < ticks; i++)
            wind.tick(rainLevel, thundering, false, day, random);
        return wind;
    }

    @Test
    void betweenStormsALightBreeze() {
        assertEquals(Wind.BREEZE, blow(0F, false, 1, 500).speed(), 1e-4F);
        assertTrue(Wind.BREEZE < Wind.MAX_SPEED * Wind.CALM_FRACTION, "lighter than the lightest storm");
    }

    @Test
    void heavierStormsBlowHarder() {
        var light = blow(0.2F, false, 1, 500).speed();
        var heavy = blow(1F, false, 1, 500).speed();
        var thunder = blow(1F, true, 1, 500).speed();
        assertTrue(light > 0F);
        assertTrue(heavy > light);
        assertTrue(thunder > heavy);
        assertEquals(Wind.MAX_SPEED * Wind.THUNDER_FACTOR, thunder, 0.01F);
    }

    @Test
    void dustStormsBlowHardest() {
        var wind = new Wind();
        var random = Randomizer.create(11);
        for (int i = 0; i < 500; i++)
            wind.tick(1F, false, true, 1, random);
        assertEquals(Wind.MAX_SPEED * Wind.DUST_FACTOR, wind.speed(), 0.01F);
        assertTrue(wind.speed() > blow(1F, true, 1, 500).speed(), "harder than a thunderstorm");
    }

    @Test
    void theWindsVectorFollowsItsSpeedAndDirection() {
        var wind = blow(1F, false, 7, 300);
        assertEquals(wind.speed(), (float) Math.hypot(wind.x(), wind.z()), 1e-5F);
        assertEquals(wind.directionX() * wind.speed(), wind.x(), 1e-6F);
        assertEquals(wind.directionZ() * wind.speed(), wind.z(), 1e-6F);
        assertEquals(1F, (float) Math.hypot(wind.directionX(), wind.directionZ()), 1e-5F);
    }

    @Test
    void theWindBuildsUpOverSeconds() {
        // After a second it is well short of a full storm's wind
        assertTrue(blow(1F, false, 1, 20).speed() < 0.5F * Wind.MAX_SPEED);
    }

    @Test
    void everyPlayerStartsFromTheDaysDirection() {
        // The wandering is each player's own, but only a little in the first ticks
        var a = new Wind();
        var b = new Wind();
        a.tick(1F, false, false, 12, Randomizer.create(1));
        b.tick(1F, false, false, 12, Randomizer.create(2));
        assertEquals(a.directionX(), b.directionX(), 0.01F);
        assertEquals(a.directionZ(), b.directionZ(), 0.01F);

        var other = new Wind();
        other.tick(1F, false, false, 13, Randomizer.create(1));
        assertNotEquals(a.directionX(), other.directionX(), 0.01F);
    }

    @Test
    void theDirectionWandersSlowly() {
        var wind = new Wind();
        var random = Randomizer.create(5);
        wind.tick(1F, false, false, 3, random);
        float x = wind.directionX(), z = wind.directionZ();
        for (int i = 0; i < 20; i++)
            wind.tick(1F, false, false, 3, random);
        // At most MAX_WANDER a tick
        var turned = Math.acos(Math.clamp(x * wind.directionX() + z * wind.directionZ(), -1F, 1F));
        assertTrue(turned <= 20 * Wind.MAX_WANDER + 1e-4, () -> "turned " + turned);
    }

    @Test
    void gustsComeAndGo() {
        var wind = new Wind();
        var random = Randomizer.create(8);
        boolean sawGust = false, sawCalm = false;
        for (int i = 0; i < 2000; i++) {
            wind.tick(1F, false, false, 1, random);
            if (wind.isGusting())
                sawGust = true;
            else
                sawCalm = true;
        }
        assertTrue(sawGust && sawCalm);
    }

    @Test
    void aGustSweepsAcrossFromUpwind() {
        var wind = new Wind();
        wind.gust(20, 1F);
        float upwind = -Wind.DEFAULT_REACH, downwind = Wind.DEFAULT_REACH;

        // Just set out: only the upwind edge feels it, a little
        assertEquals(1F, wind.gustFactor(downwind, 0F));
        assertEquals(1F, wind.gustFactor(0F, 0F));

        // Half way through passing the upwind edge it is at its height there, and hasn't reached downwind
        var random = Randomizer.create(3);
        for (int i = 0; i < 10; i++)
            wind.tick(1F, false, false, 1, random);
        assertEquals(2F, wind.gustFactor(upwind, 0F), 1e-4F);
        assertEquals(1F, wind.gustFactor(downwind, 0F));

        // Later it has passed upwind and reached downwind
        int toCross = (int) Math.ceil(2F * Wind.DEFAULT_REACH / Wind.GUST_TRAVEL);
        for (int i = 0; i < toCross; i++)
            wind.tick(1F, false, false, 1, random);
        assertEquals(1F, wind.gustFactor(upwind, 0F));
        assertTrue(wind.gustFactor(downwind, 0F) > 1.5F);
    }

    @Test
    void bearingsAreClockwiseFromNorth() {
        // North is toward negative z, east toward positive x
        assertEquals(0F, Wind.bearing(0F, -1F), 1e-3F);
        assertEquals(90F, Wind.bearing(1F, 0F), 1e-3F);
        assertEquals(180F, Wind.bearing(0F, 1F), 1e-3F);
        assertEquals(270F, Wind.bearing(-1F, 0F), 1e-3F);
        assertEquals(45F, Wind.bearing(1F, -1F), 1e-3F);
    }

    @Test
    void bearingsRoundToTheNearestCompassPoint() {
        assertEquals("N", Wind.compassPoint(0F));
        assertEquals("N", Wind.compassPoint(22F));
        assertEquals("NE", Wind.compassPoint(23F));
        assertEquals("E", Wind.compassPoint(90F));
        assertEquals("SW", Wind.compassPoint(225F));
        assertEquals("NW", Wind.compassPoint(330F));
        assertEquals("N", Wind.compassPoint(359F));
    }

    @Test
    void aGustSweepsTheWholeOfAWiderRange() {
        var wind = new Wind();
        wind.setReach(24F);
        wind.gust(20, 1F);
        // Its front sets out from the edge of the wider range, and is still under way when it reaches the far side
        var random = Randomizer.create(3);
        for (int i = 0; i < 10; i++)
            wind.tick(1F, false, false, 1, random);
        assertEquals(2F, wind.gustFactor(-24F, 0F), 1e-4F);
        int toCross = (int) Math.ceil(48F / Wind.GUST_TRAVEL);
        for (int i = 0; i < toCross; i++)
            wind.tick(1F, false, false, 1, random);
        assertTrue(wind.isGusting());
        assertTrue(wind.gustFactor(24F, 0F) > 1.5F, "reached the far side");
    }

    @Test
    void gustsStartAtTheEdgeOfTheDefaultRange() {
        assertEquals(PrecipitationField.DEFAULT_RADIUS, Wind.DEFAULT_REACH, 1e-6F);
    }

    @Test
    void aBearingTurnsIntoTheSameDirection() {
        for (float bearing : new float[]{0F, 45F, 90F, 135F, 180F, 225F, 270F, 315F, 10F, 359F}) {
            var wind = new Wind();
            wind.setBearing(bearing);
            assertEquals(bearing, wind.bearing(), 0.01F, "bearing " + bearing);
        }
    }

    @Test
    void aSetSpeedAndDirectionHold() {
        var wind = new Wind();
        wind.setSpeed(0.4F);
        wind.setBearing(90F);
        var random = Randomizer.create(17);
        // A light storm would ease the speed down, the direction would wander, and a new day turn it: none of them do
        for (int i = 0; i < 400; i++)
            wind.tick(0.2F, false, false, i < 200 ? 1 : 2, random);
        assertEquals(0.4F, wind.speed(), 1e-6F);
        assertEquals(90F, wind.bearing(), 0.01F);
        assertTrue(wind.isSet() && wind.isSpeedSet() && wind.isBearingSet());
    }

    @Test
    void speedAndDirectionAreSetApart() {
        var wind = new Wind();
        wind.setBearing(180F);
        var random = Randomizer.create(19);
        for (int i = 0; i < 300; i++)
            wind.tick(1F, false, false, 1, random);
        assertTrue(wind.isBearingSet() && !wind.isSpeedSet());
        assertEquals(Wind.MAX_SPEED, wind.speed(), 0.01F, "the speed still follows the storm");
    }

    @Test
    void clearingLetsTheWindGoItsOwnWay() {
        var wind = new Wind();
        wind.setSpeed(0.6F);
        wind.clearSet();
        assertFalse(wind.isSet());
        var random = Randomizer.create(23);
        for (int i = 0; i < 500; i++)
            wind.tick(1F, false, false, 1, random);
        assertEquals(Wind.MAX_SPEED, wind.speed(), 0.01F, "eased back to the storm's");
    }

    @Test
    void percentsAreOfAFullStormsWind() {
        assertEquals(Wind.MAX_SPEED, Wind.speedOfPercent(100), 1e-6F);
        assertEquals(100, Wind.percentOfFullStorm(Wind.MAX_SPEED));
        assertEquals(250, Wind.percentOfFullStorm(Wind.speedOfPercent(250)));
    }

    @Test
    void compassPointsHaveTheirBearings() {
        assertEquals(8, Wind.compassPoints().size());
        assertEquals(0F, Wind.bearingOf("N"));
        assertEquals(90F, Wind.bearingOf("E"));
        assertEquals(225F, Wind.bearingOf("SW"));
        for (var point : Wind.compassPoints())
            assertEquals(point, Wind.compassPoint(Wind.bearingOf(point)));
    }

    @Test
    void outsideAGustTheWindIsSteady() {
        var wind = new Wind();
        for (float along = -12F; along <= 12F; along += 1F)
            assertEquals(1F, wind.gustFactor(along, 0.5F));
    }
}

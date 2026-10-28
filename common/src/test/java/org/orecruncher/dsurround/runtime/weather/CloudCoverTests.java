package org.orecruncher.dsurround.runtime.weather;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for what the clouds are like for the weather, the time of day, the biome and the day.
 */
public class CloudCoverTests {

    // 2 pm and 2 am
    private static final long AFTERNOON = 8000;
    private static final long NIGHT = 20000;

    @Test
    void fairWeatherCloudsBuildUpThroughTheAfternoon() {
        var afternoon = CloudCover.of(0F, 0F, AFTERNOON, 0.8F, 0.5F);
        var night = CloudCover.of(0F, 0F, NIGHT, 0.8F, 0.5F);
        assertTrue(afternoon.maxThickness() > night.maxThickness() + 10F,
                "afternoon " + afternoon.maxThickness() + ", night " + night.maxThickness());
        assertTrue(night.maxThickness() < CloudCover.FLAT + 4F, "flat at night: " + night.maxThickness());
        assertEquals(CloudCover.FLAT, afternoon.minThickness(), 1e-6F, "the thinnest stay vanilla's");
        assertEquals(0F, afternoon.baseDrop(), 1e-6F, "the base stays where vanilla's is");
    }

    @Test
    void convectionChangesSmoothlyThroughTheDay() {
        float last = CloudCover.convection(0);
        for (long t = 50; t < 24000; t += 50) {
            float now = CloudCover.convection(t);
            assertTrue(Math.abs(now - last) < 0.05F, "a jump at " + t);
            last = now;
        }
        assertEquals(CloudCover.convection(AFTERNOON), CloudCover.convection(AFTERNOON + 24000), 1e-6F);
    }

    @Test
    void wetBiomesAndCloudyDaysHaveMoreClouds() {
        assertTrue(CloudCover.of(0F, 0F, AFTERNOON, 1F, 0.5F).coverage() > CloudCover.of(0F, 0F, AFTERNOON, 0F, 0.5F).coverage());
        assertTrue(CloudCover.of(0F, 0F, AFTERNOON, 0.5F, 1F).coverage() > CloudCover.of(0F, 0F, AFTERNOON, 0.5F, 0F).coverage());
        assertTrue(CloudCover.of(0F, 0F, AFTERNOON, 1F, 0.5F).maxThickness() > CloudCover.of(0F, 0F, AFTERNOON, 0F, 0.5F).maxThickness(),
                "wet air builds taller");
    }

    @Test
    void aStormClosesTheSkyLowersAndThickens() {
        var fair = CloudCover.of(0F, 0F, AFTERNOON, 0.5F, 0.3F);
        var drizzle = CloudCover.of(CloudCover.CLOSED_BY, 0F, AFTERNOON, 0.5F, 0.3F);
        var storm = CloudCover.of(1F, 0F, AFTERNOON, 0.5F, 0.3F);
        assertEquals(CloudCover.STORM_COVERAGE, drizzle.coverage(), 1e-6F, "closed over by a moderate storm");
        assertEquals(CloudCover.STORM_COVERAGE, storm.coverage(), 1e-6F);
        assertTrue(storm.baseDrop() > drizzle.baseDrop() && drizzle.baseDrop() > fair.baseDrop());
        assertTrue(storm.minThickness() > drizzle.minThickness() && drizzle.minThickness() > fair.minThickness());
        assertEquals(CloudCover.STORM_MIN, storm.minThickness(), 1e-6F);
        assertTrue(storm.maxThickness() >= CloudCover.STORM_MAX);
        assertTrue(storm.overcast() > fair.overcast());
        assertEquals(0F, storm.cirrus(), 1e-6F, "the deck hides the streaks above it");
    }

    @Test
    void thunderTowersAboveTheDeck() {
        var storm = CloudCover.of(1F, 0F, NIGHT, 0.5F, 0.3F);
        var thunder = CloudCover.of(1F, 1F, NIGHT, 0.5F, 0.3F);
        assertEquals(storm.maxThickness() + CloudCover.THUNDER_TOWER, thunder.maxThickness(), 1e-4F);
        assertTrue(thunder.baseDrop() > storm.baseDrop());
        assertEquals(1F, thunder.overcast(), 1e-6F);
        assertTrue(thunder.minThickness() <= thunder.maxThickness());
    }

    @Test
    void theStrongestStormStillLeavesTheThickestUnderTheBuildHeight() {
        // From the overworld's cloud height (192), the tallest tower stays inside a 320 build height's sky
        var worst = CloudCover.of(1F, 1F, AFTERNOON, 1F, 1F);
        assertTrue(192 - worst.baseDrop() + worst.maxThickness() < 320F, "top " + (192 - worst.baseDrop() + worst.maxThickness()));
    }

    @Test
    void eachDayHasItsOwnCloudinessEasingFromOneToTheNext() {
        float day0 = CloudCover.cloudinessOf(0);
        float day1 = CloudCover.cloudinessOf(1);
        assertNotEquals(day0, day1);
        // The same for everyone
        assertEquals(day0, CloudCover.cloudinessOf(0));
        // Through the day, that day's; by the next sunrise, the next's; no jumps between
        assertEquals(day0, CloudCover.dayCover(AFTERNOON - 4000), 1e-6F);
        assertEquals(day1, CloudCover.dayCover(24000), 1e-6F);
        float last = CloudCover.dayCover(0);
        for (long t = 100; t < 48000; t += 100) {
            float now = CloudCover.dayCover(t);
            assertTrue(Math.abs(now - last) < 0.02F, "a jump at " + t);
            last = now;
        }
        // Spread over the range
        float least = 1F, most = 0F;
        for (long d = 0; d < 200; d++) {
            least = Math.min(least, CloudCover.cloudinessOf(d));
            most = Math.max(most, CloudCover.cloudinessOf(d));
        }
        assertTrue(least < 0.1F && most > 0.9F, least + " to " + most);
    }

    @Test
    void heldByCommandKeepsTheRestOfTheWeathers() {
        var storm = CloudCover.of(1F, 0F, AFTERNOON, 0.5F, 0.5F);
        var held = storm.held(0.3F, 60F);
        assertEquals(0.3F, held.coverage(), 1e-6F);
        assertEquals(60F, held.maxThickness(), 1e-6F);
        assertEquals(storm.baseDrop(), held.baseDrop(), 1e-6F);
        assertEquals(storm.overcast(), held.overcast(), 1e-6F);
        assertEquals(storm.minThickness(), held.minThickness(), 1e-6F);

        // Thinner than the storm's thinnest: all of them that thick
        var thin = storm.held(Float.NaN, 8F);
        assertEquals(storm.coverage(), thin.coverage(), 1e-6F, "NaN leaves it");
        assertEquals(8F, thin.minThickness(), 1e-6F);
        assertEquals(8F, thin.maxThickness(), 1e-6F);
        assertEquals(storm, storm.held(Float.NaN, Float.NaN));
    }
}

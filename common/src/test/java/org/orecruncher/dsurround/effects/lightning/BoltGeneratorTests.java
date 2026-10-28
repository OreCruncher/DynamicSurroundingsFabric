package org.orecruncher.dsurround.effects.lightning;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;

import static org.junit.jupiter.api.Assertions.*;

public class BoltGeneratorTests {

    private static final int SEEDS = 200;

    private static BoltShape ground(long seed, float intensity) {
        return BoltGenerator.groundStrike(20F, -130F, -10F, intensity, Randomizer.create(seed));
    }

    @Test
    void theMainChannelRunsFromTheCloudToTheGround() {
        var main = ground(1L, 0.5F).main();
        assertEquals(0, main.depth());
        assertEquals(0F, main.x(0));
        assertEquals(0F, main.y(0));
        assertEquals(0F, main.z(0));
        int end = main.size() - 1;
        assertEquals(20F, main.x(end));
        assertEquals(-130F, main.y(end));
        assertEquals(-10F, main.z(end));
    }

    @Test
    void theMainChannelIsJaggedAndFinelySplit() {
        var main = ground(2L, 0.5F).main();
        // 132 blocks, split to pieces under 2.5 blocks: 64 pieces
        assertEquals(65, main.size());
        assertTrue(BoltGenerator.pathLength(main.points()) > 140F, "no wander in it");
    }

    @Test
    void theSameSeedMakesTheSameBolt() {
        var a = ground(42L, 0.7F);
        var b = ground(42L, 0.7F);
        assertEquals(a.channels().size(), b.channels().size());
        for (int c = 0; c < a.channels().size(); c++)
            assertArrayEquals(a.channels().get(c).points(), b.channels().get(c).points());
    }

    @Test
    void theLeaderReachesEachPointInTurn() {
        var shape = ground(3L, 1F);
        var main = shape.main();
        assertEquals(0F, main.arrival()[0]);
        assertEquals(1F, main.arrival()[main.size() - 1], 1e-4F);
        for (var channel : shape.channels()) {
            var arrival = channel.arrival();
            for (int i = 1; i < arrival.length; i++)
                assertTrue(arrival[i] > arrival[i - 1], "the leader went back");
        }
    }

    @Test
    void aBranchStartsOnItsParentWhenTheLeaderGetsThere() {
        var shape = ground(4L, 1F);
        var main = shape.main();
        for (var channel : shape.channels()) {
            if (channel.depth() != 1)
                continue;
            boolean found = false;
            for (int i = 0; i < main.size() && !found; i++)
                found = main.x(i) == channel.x(0) && main.y(i) == channel.y(0) && main.z(i) == channel.z(0)
                        && main.arrival()[i] == channel.arrival()[0];
            assertTrue(found, "a branch doesn't start on the main channel");
        }
    }

    @Test
    void strongerStormsBranchMoreAndDeeper() {
        int weakBranches = 0, strongBranches = 0, weakDepth = 0, strongDepth = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            var weak = ground(seed, 0.1F);
            var strong = ground(seed, 1F);
            weakBranches += weak.channels().size() - 1;
            strongBranches += strong.channels().size() - 1;
            weakDepth = Math.max(weakDepth, weak.depth());
            strongDepth = Math.max(strongDepth, strong.depth());
            assertTrue(weak.depth() <= BoltGenerator.maxDepth(0.1F));
            assertTrue(strong.depth() <= BoltGenerator.maxDepth(1F));
        }
        assertTrue(strongBranches > weakBranches * 3, "strong %d, weak %d".formatted(strongBranches, weakBranches));
        assertEquals(1, weakDepth);
        assertEquals(3, strongDepth);
    }

    @Test
    void depthAndBranchCountsFollowTheStorm() {
        assertEquals(1, BoltGenerator.maxDepth(0F));
        assertEquals(2, BoltGenerator.maxDepth(0.5F));
        assertEquals(3, BoltGenerator.maxDepth(1F));
        assertEquals(3, BoltGenerator.maxDepth(5F));
        assertEquals(2F, BoltGenerator.mainBranches(0F));
        assertEquals(10F, BoltGenerator.mainBranches(1F));
        assertTrue(BoltGenerator.subBranches(1F, 1) > BoltGenerator.subBranches(1F, 2));
    }

    @Test
    void aGroundStrikesBranchesFallAndStopShortOfTheGround() {
        for (long seed = 0; seed < SEEDS; seed++) {
            var shape = ground(seed, 1F);
            for (var channel : shape.channels()) {
                if (channel.depth() == 0)
                    continue;
                int end = channel.size() - 1;
                assertTrue(channel.y(end) < channel.y(0), "a branch rises");
            }
        }
    }

    @Test
    void spiderLightningSpreadsLevel() {
        for (long seed = 0; seed < SEEDS; seed++) {
            var shape = BoltGenerator.spider(150F, -10F, 40F, 1F, Randomizer.create(seed));
            for (var channel : shape.channels()) {
                int end = channel.size() - 1;
                float dx = channel.x(end) - channel.x(0), dz = channel.z(end) - channel.z(0);
                float drop = channel.y(0) - channel.y(end);
                float across = (float) Math.sqrt(dx * dx + dz * dz);
                // Level or sloping gently down: no further down than across, nor more than a little up
                assertTrue(drop <= across, "spider lightning dives");
                assertTrue(drop > -0.1F * across - 1F, "spider lightning climbs");
            }
        }
    }

    @Test
    void aBoltHasNoMoreThanTheMostPoints() {
        for (long seed = 0; seed < SEEDS; seed++) {
            var shape = BoltGenerator.groundStrike(60F, -160F, 60F, 1F, Randomizer.create(seed));
            // The last branch added may take it a little over
            assertTrue(shape.pointCount() < BoltGenerator.MAX_POINTS + 129, "points: " + shape.pointCount());
        }
    }

    @Test
    void aShortLineIsNotSplit() {
        var points = BoltGenerator.line(0F, 0F, 0F, 1F, 1F, 0F, Randomizer.create(5L));
        assertArrayEquals(new float[]{0F, 0F, 0F, 1F, 1F, 0F}, points);
    }

    @Test
    void perpendicularIsAtRightAnglesAndOfLengthOne() {
        var random = Randomizer.create(6L);
        float[][] directions = {{0F, 1F, 0F}, {1F, 0F, 0F}, {0F, 0F, -1F}, {0.6F, -0.8F, 0F}};
        for (var t : directions) {
            for (int i = 0; i < 50; i++) {
                var p = BoltGenerator.perpendicular(t[0], t[1], t[2], random);
                assertEquals(0F, p[0] * t[0] + p[1] * t[1] + p[2] * t[2], 1e-4F);
                assertEquals(1F, (float) Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]), 1e-4F);
            }
        }
    }
}

package org.orecruncher.dsurround.effects.lightning;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.*;

public class StrikesTests {

    @Test
    void noThunderNoLightning() {
        assertEquals(0F, Strikes.intensity(0F, 1F));
        assertEquals(0F, Strikes.chancePerTick(0F));
    }

    @Test
    void lightningFollowsTheThunderAndTheStorm() {
        assertEquals(1F, Strikes.intensity(1F, 1F), 1e-6F);
        // A weak storm still has some
        assertEquals(0.3F, Strikes.intensity(1F, 0F), 1e-6F);
        assertTrue(Strikes.intensity(1F, 0.5F) > Strikes.intensity(0.5F, 0.5F));
        assertTrue(Strikes.chancePerTick(1F) > Strikes.chancePerTick(0.2F));
        // At full strength, a flash every few seconds
        assertEquals(Strikes.FLASHES_PER_SECOND / 20F, Strikes.chancePerTick(1F), 1e-6F);
    }

    @Test
    void strikesStayInsideTheView() {
        assertEquals(Strikes.FARTHEST, Strikes.farthest(2000F));
        // Render distance 4: the view reaches 256 blocks
        assertEquals(256F * 0.85F, Strikes.farthest(256F), 1e-3F);
    }

    @Test
    void distancesAreSpreadBetweenTheNearestAndFarthest() {
        var random = Randomizer.create(8L);
        int farHalf = 0;
        for (int i = 0; i < 2000; i++) {
            float d = Strikes.spreadDistance(64F, 400F, random);
            assertTrue(d >= 64F && d <= 400F, "distance " + d);
            if (d > 232F)
                farHalf++;
        }
        // More ground far away: most strikes are in the farther half
        assertTrue(farHalf > 1200, "far half: " + farHalf);
        assertEquals(64F, Strikes.spreadDistance(64F, 30F, random));
    }

    @Test
    void strongerStormsHaveMoreBoltsAndSpiders() {
        var weak = kinds(0F);
        var strong = kinds(1F);
        assertTrue(strong.get(Strike.Kind.SPIDER) > weak.get(Strike.Kind.SPIDER));
        assertTrue(strong.get(Strike.Kind.GROUND) > weak.get(Strike.Kind.GROUND));
        assertTrue(weak.get(Strike.Kind.SHEET) > strong.get(Strike.Kind.SHEET));
    }

    private static EnumMap<Strike.Kind, Integer> kinds(float intensity) {
        var random = Randomizer.create(9L);
        var counts = new EnumMap<Strike.Kind, Integer>(Strike.Kind.class);
        for (var kind : Strike.Kind.values())
            counts.put(kind, 0);
        for (int i = 0; i < 5000; i++)
            counts.merge(Strikes.pickKind(intensity, random), 1, Integer::sum);
        return counts;
    }

    // How far round from dead upwind an angle is, 0 to pi
    private static double offUpwind(float angle, float windX, float windZ) {
        double x = Math.cos(angle), z = Math.sin(angle);
        return Math.acos(Math.clamp(-(x * windX + z * windZ), -1D, 1D));
    }

    @Test
    void mostStrikesComeFromUpwind() {
        var random = Randomizer.create(11L);
        // Blowing toward the east (+x): upwind is the west
        int upwind = 0, downwind = 0;
        for (int i = 0; i < 5000; i++) {
            double off = offUpwind(Strikes.strikeAngle(1F, 0F, random), 1F, 0F);
            if (off < Math.PI / 4)
                upwind++;
            else if (off > Math.PI * 3 / 4)
                downwind++;
        }
        assertTrue(upwind > 2500, "upwind: " + upwind);
        // Some still land behind, from the strikes that land anywhere
        assertTrue(downwind > 100 && downwind < 500, "downwind: " + downwind);
    }

    @Test
    void strikesFromUpwindStayWithinTheSpread() {
        var random = Randomizer.create(12L);
        int outside = 0;
        for (int i = 0; i < 5000; i++)
            if (offUpwind(Strikes.strikeAngle(0F, -1F, random), 0F, -1F) > Strikes.UPWIND_SPREAD + 1e-4)
                outside++;
        // Only the share that lands anywhere, and of those only the part outside the spread
        assertTrue(outside < 5000 * Strikes.ANYWHERE, "outside: " + outside);
    }

    @Test
    void withoutWindStrikesComeFromAnywhere() {
        var random = Randomizer.create(13L);
        int[] quarters = new int[4];
        for (int i = 0; i < 8000; i++) {
            double angle = Math.floorMod((long) Math.floor(Strikes.strikeAngle(0F, 0F, random) / (Math.PI / 2)), 4L);
            quarters[(int) angle]++;
        }
        for (var count : quarters)
            assertTrue(count > 1700 && count < 2300, "quarter: " + count);
    }

    @Test
    void thunderTravelsAtTheSpeedOfSound() {
        // 343 blocks: a second
        assertEquals(20, Strikes.thunderDelay(343F));
        assertEquals(0, Strikes.thunderDelay(0F));
        assertEquals(10, Strikes.thunderDelay(171.5F));
    }

    @Test
    void farThunderIsQuieterAndLower() {
        assertEquals(1F, Strikes.thunderVolume(Strikes.NEAREST));
        assertEquals(0.3F, Strikes.thunderVolume(Strikes.FARTHEST), 1e-6F);
        assertTrue(Strikes.thunderVolume(200F) < 1F);
        assertEquals(1F, Strikes.thunderPitch(10F));
        assertEquals(0.75F, Strikes.thunderPitch(1000F), 1e-6F);
    }

    @Test
    void eachKindIsMadeAsAsked() {
        var random = Randomizer.create(10L);
        var ground = Strikes.ground(0D, 200D, 0D, 10F, -130F, 0F, 0.5F, 120F, true, 0D, 7, random);
        assertEquals(Strike.Kind.GROUND, ground.kind());
        assertNotNull(ground.shape());
        assertEquals(7, ground.boltId());
        assertTrue(ground.inLeader(0F));

        var spider = Strikes.spider(0D, 180D, 0D, 150F, -8F, 0F, 0.5F, 120F, true, 0D, random);
        assertEquals(Strike.Kind.SPIDER, spider.kind());
        // The cloud glows where it starts, midway and at its end
        assertEquals(3, spider.glow().count());
        assertEquals(-1, spider.boltId());

        var sheet = Strikes.sheet(0D, 180D, 0D, 0.5F, 120F, false, 0D, random);
        assertEquals(Strike.Kind.SHEET, sheet.kind());
        assertNull(sheet.shape());
        // Without flickering, one stroke; and no leader
        assertEquals(1, sheet.strokes());
        assertFalse(sheet.inLeader(0F));
        // Several patches of lit cloud, not one round glow
        assertTrue(sheet.glow().count() >= 4);
    }

    @Test
    void farStrikesShowLess() {
        assertEquals(1F, Strikes.visibility(50F));
        assertEquals(0.5F, Strikes.visibility(Strikes.FARTHEST), 1e-6F);
    }
}

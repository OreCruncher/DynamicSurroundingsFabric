package org.orecruncher.dsurround.effects.precipitation;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the color dust takes: the region's loose ground, whatever it blows over.
 */
public class PrecipitationColumnsTests {

    private static final int RADIUS = 12;
    private static final int SAND = 0xF7E9A3;
    private static final int RED_SAND = 0xD87F33;
    private static final int FALLBACK = 0x123456;

    /**
     * A desert on flat ground, dust everywhere, its ground's color by {@code colorAt(x, z)}
     */
    private record Desert(IntBinaryOperator colorAt) implements IColumnSource {
        @Override
        public int groundHeight(int x, int z) {
            return 64;
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
            return this.colorAt.applyAsInt(x, z);
        }

        @Override
        public int fallbackDustColor(int x, int y, int z) {
            return FALLBACK;
        }
    }

    private static PrecipitationColumns look(IntBinaryOperator colorAt) {
        var columns = new PrecipitationColumns(RADIUS);
        columns.update(new Desert(colorAt), 0, 70, 0);
        return columns;
    }

    @Test
    void dustOverGrassOrWaterIsTheRegionsSand() {
        // Sand to the north, grass (or water) to the south: no color of its own
        var columns = look((x, z) -> z < 0 ? SAND : IColumnSource.NOT_A_DUST_SOURCE);
        assertEquals(SAND, columns.regionalDustColor());
        assertEquals(SAND, columns.dustColorAt(columns.index(0, 5)), "over the grass");
        assertEquals(SAND, columns.dustColorAt(columns.index(0, -5)), "over the sand");
    }

    @Test
    void withNoLooseGroundInSightTheBiomeDecides() {
        var columns = look((x, z) -> IColumnSource.NOT_A_DUST_SOURCE);
        assertEquals(FALLBACK, columns.regionalDustColor());
        assertEquals(FALLBACK, columns.dustColorAt(columns.index(3, 3)));
    }

    @Test
    void mixedGroundAveragesOut() {
        // Red sand to the west, sand to the east: the region's color is between the two
        var columns = look((x, z) -> x < 0 ? RED_SAND : SAND);
        int regional = columns.regionalDustColor();
        assertTrue(between((regional >> 16) & 0xFF, (RED_SAND >> 16) & 0xFF, (SAND >> 16) & 0xFF));
        assertTrue(between((regional >> 8) & 0xFF, (RED_SAND >> 8) & 0xFF, (SAND >> 8) & 0xFF));
        assertTrue(between(regional & 0xFF, RED_SAND & 0xFF, SAND & 0xFF));
    }

    @Test
    void overLooseGroundItIsHalfItsOwnColor() {
        // One patch of red sand in a sand desert: dust rising from it is half red sand, half the region's sand
        var columns = look((x, z) -> x == 2 && z == 2 ? RED_SAND : SAND);
        int regional = columns.regionalDustColor();
        int patch = columns.dustColorAt(columns.index(2, 2));
        assertEquals((((RED_SAND >> 16) & 0xFF) + ((regional >> 16) & 0xFF)) / 2, (patch >> 16) & 0xFF);
        assertEquals((((RED_SAND >> 8) & 0xFF) + ((regional >> 8) & 0xFF)) / 2, (patch >> 8) & 0xFF);
        assertEquals(((RED_SAND & 0xFF) + (regional & 0xFF)) / 2, patch & 0xFF);
        assertNotEquals(regional, patch);
    }

    /**
     * Flat ground at a height that can be changed, rain where x is below {@code rainBelowX} and nothing beyond; counts
     * how often its heights are read
     */
    private static final class Changing implements IColumnSource {
        int ground = 64;
        int rainBelowX = Integer.MAX_VALUE;
        int heightReads;

        @Override
        public int groundHeight(int x, int z) {
            this.heightReads++;
            return this.ground;
        }

        @Override
        public PrecipitationKind precipitation(int x, int y, int z) {
            return x < this.rainBelowX ? PrecipitationKind.RAIN : PrecipitationKind.NONE;
        }

        @Override
        public int light(int x, int y, int z) {
            return 0xF000F0;
        }

        @Override
        public int dustColor(int x, int y, int z) {
            return IColumnSource.NOT_A_DUST_SOURCE;
        }

        @Override
        public int fallbackDustColor(int x, int y, int z) {
            return FALLBACK;
        }
    }

    @Test
    void standingStillAFewRowsAreLookedAtAgainEachTick() {
        var source = new Changing();
        var columns = new PrecipitationColumns(RADIUS);
        assertTrue(columns.refresh(source, 0, 70, 0), "the first look");
        int size = 2 * RADIUS + 1;
        assertEquals(columns.columnCount(), source.heightReads, "every column, the first time");

        // The ground changes; a few rows see it each tick, and all of them within the refresh interval
        source.ground = 66;
        int rows = PrecipitationColumns.rowsPerTick(size);
        for (int t = 0; t < PrecipitationColumns.REFRESH_TICKS; t++) {
            int before = source.heightReads;
            columns.refresh(source, 0, 70, 0);
            int read = source.heightReads - before;
            assertTrue(read > 0 && read <= rows * size, "a few rows a tick, not " + read);
        }
        int inArea = 0;
        for (int z = -RADIUS; z <= RADIUS; z++)
            for (int x = -RADIUS; x <= RADIUS; x++) {
                int index = columns.index(x, z);
                if (index < 0)
                    continue;
                inArea++;
                assertEquals(66, columns.ground(index), "column " + x + ", " + z);
            }
        assertEquals(columns.columnCount(), inArea);
    }

    /**
     * The columns of the disc around a camera column, as x and z packed in a long
     */
    private static Set<Long> disc(int cameraX, int cameraZ) {
        var columns = new HashSet<Long>();
        for (int dz = -RADIUS; dz <= RADIUS; dz++)
            for (int dx = -RADIUS; dx <= RADIUS; dx++)
                if (PrecipitationColumns.inDisc(dx, dz, RADIUS))
                    columns.add(pack(cameraX + dx, cameraZ + dz));
        return columns;
    }

    private static long pack(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }

    @Test
    void movingLooksUpOnlyTheColumnsThatCameIntoTheArea() {
        var source = new Changing();
        var columns = new PrecipitationColumns(RADIUS);
        columns.refresh(source, 0, 70, 0);
        int size = 2 * RADIUS + 1;

        // A column east: the columns that came into the disc, besides the rows due a fresh look (the area's first
        // rows, the first tick after a full look)
        int before = source.heightReads;
        assertTrue(columns.refresh(source, 1, 70, 0));
        int rows = PrecipitationColumns.rowsPerTick(size);
        var expected = disc(1, 0);
        var entered = new HashSet<>(expected);
        entered.removeAll(disc(0, 0));
        expected.removeIf(c -> {
            int z = (int) c.longValue();
            return z - (0 - RADIUS) >= rows && !entered.contains(c);
        });
        assertEquals(expected.size(), source.heightReads - before);
        assertTrue(columns.index(1 + RADIUS, 0) >= 0, "the new edge is in the area");
        assertEquals(-1, columns.index(-RADIUS, 0), "the old edge has left it");
    }

    @Test
    void theRunningTotalsMatchAFreshLookAfterMoving() {
        // Rain to the west, and a desert of red sand and sand to the east: walking east changes every share
        IColumnSource mixed = new IColumnSource() {
            @Override
            public int groundHeight(int x, int z) {
                return 64;
            }

            @Override
            public PrecipitationKind precipitation(int x, int y, int z) {
                return x < 0 ? PrecipitationKind.RAIN : PrecipitationKind.DUST;
            }

            @Override
            public int light(int x, int y, int z) {
                return 0xF000F0;
            }

            @Override
            public int dustColor(int x, int y, int z) {
                return z < 3 ? RED_SAND : (x % 3 == 0 ? IColumnSource.NOT_A_DUST_SOURCE : SAND);
            }

            @Override
            public int fallbackDustColor(int x, int y, int z) {
                return FALLBACK;
            }
        };
        var walked = new PrecipitationColumns(RADIUS);
        walked.refresh(mixed, -20, 70, 0);
        for (int x = -19; x <= 15; x++)
            walked.refresh(mixed, x, 70, x / 3);

        var fresh = new PrecipitationColumns(RADIUS);
        fresh.update(mixed, 15, 70, 5);
        assertEquals(fresh.fallingShare(), walked.fallingShare(), 1e-6F);
        assertEquals(fresh.dustShare(), walked.dustShare(), 1e-6F);
        assertEquals(fresh.regionalDustColor(), walked.regionalDustColor());
        for (int z = 5 - RADIUS; z <= 5 + RADIUS; z++)
            for (int x = 15 - RADIUS; x <= 15 + RADIUS; x++) {
                int index = fresh.index(x, z);
                assertEquals(index, walked.index(x, z), "the same disc, at " + x + ", " + z);
                if (index >= 0)
                    assertEquals(fresh.kind(index), walked.kind(index), "column " + x + ", " + z);
            }
    }

    @Test
    void anInvalidatedLookIsRedone() {
        var source = new Changing();
        var columns = new PrecipitationColumns(RADIUS);
        columns.refresh(source, 0, 70, 0);

        // The ground changes; without looking again the columns still have the old height
        source.ground = 66;
        columns.refresh(source, 0, 70, 0);
        assertEquals(64, columns.ground(columns.index(0, 0)));

        columns.invalidate();
        assertTrue(columns.refresh(source, 0, 70, 0));
        assertEquals(66, columns.ground(columns.index(0, 0)));
    }

    @Test
    void theShareOfColumnsWithRainIsCounted() {
        var source = new Changing();
        // Rain west of x = 0: the disc's columns but the middle row north to south, halved
        source.rainBelowX = 0;
        var columns = new PrecipitationColumns(RADIUS);
        columns.update(source, 0, 70, 0);
        int west = (columns.columnCount() - (RADIUS * 2 + 1)) / 2;
        assertEquals(west / (float) columns.columnCount(), columns.fallingShare(), 1e-5F);
        assertEquals(0F, columns.dustShare());
    }

    @Test
    void onlyTheDiscIsLookedUp() {
        for (int radius : new int[]{8, 12, 16, 32}) {
            var source = new Changing();
            var columns = new PrecipitationColumns(radius);
            columns.update(source, 0, 70, 0);
            int square = (radius * 2 + 1) * (radius * 2 + 1);
            assertEquals(columns.columnCount(), source.heightReads, "each column read once");
            assertTrue(columns.columnCount() < square * 0.9F, "the corners are left out at " + radius);
            assertTrue(columns.columnCount() > Math.PI * radius * radius, "the whole disc at " + radius);
            assertEquals(-1, columns.index(radius, radius), "a corner");
            assertTrue(columns.index(radius, 0) >= 0, "the edge, east");
            assertTrue(columns.index(0, -radius) >= 0, "the edge, north");
        }
    }

    @Test
    void everyPlaceADropCanBeHasItsColumn() {
        // The camera anywhere in its column, drops anywhere within the radius of it, as the field keeps them
        var random = Randomizer.create(7);
        for (int radius : new int[]{8, 12, 16}) {
            var columns = new PrecipitationColumns(radius);
            columns.update(new Changing(), 0, 70, 0);
            for (int i = 0; i < 20000; i++) {
                double cameraX = random.nextDouble();
                double cameraZ = random.nextDouble();
                double angle = random.nextDouble() * Math.PI * 2;
                // Mostly at the very edge, where a column could be missed
                double r = radius * (i % 2 == 0 ? 1 - random.nextDouble() * 1e-3 : Math.sqrt(random.nextDouble()));
                double x = cameraX + Math.cos(angle) * r;
                double z = cameraZ + Math.sin(angle) * r;
                assertTrue(columns.index(x, z) >= 0, "no column at " + x + ", " + z + " for radius " + radius);
            }
        }
    }

    private static boolean between(int value, int a, int b) {
        return value >= Math.min(a, b) && value <= Math.max(a, b) && value != a && value != b;
    }
}

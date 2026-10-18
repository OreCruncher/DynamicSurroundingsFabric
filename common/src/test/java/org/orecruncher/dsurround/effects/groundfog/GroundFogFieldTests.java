package org.orecruncher.dsurround.effects.groundfog;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ground fog's cells: where the ground is under the mist, how thick it is, and which columns are looked
 * at.
 */
public class GroundFogFieldTests {

    private static final int GROUND = 64;

    /**
     * Flat ground, foggy west of {@code fogBelowX}, with columns of other heights set by hand
     */
    private static final class Ground implements IGroundFogSource {
        final int fogBelowX;
        final Map<Long, Integer> heights = new HashMap<>();
        int looks;
        int lightReads;
        int light = 0xF000F0;

        Ground(int fogBelowX) {
            this.fogBelowX = fogBelowX;
        }

        Ground column(int x, int z, int height) {
            this.heights.put(key(x, z), height);
            return this;
        }

        private static long key(int x, int z) {
            return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        }

        @Override
        public int groundHeight(int x, int z) {
            this.looks++;
            return this.heights.getOrDefault(key(x, z), GROUND);
        }

        @Override
        public float density(int x, int y, int z) {
            return x < this.fogBelowX ? 0.8F : 0F;
        }

        @Override
        public int light(int x, int y, int z) {
            this.lightReads++;
            return this.light;
        }
    }

    /**
     * The cell under the camera, which is at the middle of the area
     */
    private static int middle(GroundFogField field) {
        return field.radius();
    }

    @Test
    void theMistLiesOnTheGround() {
        var field = new GroundFogField(8);
        field.update(new Ground(Integer.MAX_VALUE), 0.5, 0.5, Float.NaN);
        for (int j = 0; j < field.size(); j++)
            for (int i = 0; i < field.size(); i++)
                assertEquals(GROUND, field.height(i, j), 1e-6F);
    }

    @Test
    void aPostOrTrunkStandsInTheMist() {
        // Regression: a tree trunk raised a spike in the mist. A single tall column, or a 2 by 2 trunk, is well above
        // most of the columns around it, so the mist is at the ground's height there
        var ground = new Ground(Integer.MAX_VALUE).column(0, 0, GROUND + 6)
                .column(3, 3, GROUND + 8).column(4, 3, GROUND + 8).column(3, 4, GROUND + 8).column(4, 4, GROUND + 8);
        var field = new GroundFogField(8);
        field.update(ground, 0.5, 0.5, Float.NaN);
        int m = middle(field);
        assertEquals(GROUND, field.height(m, m), 1e-6F, "the post");
        for (int dz = 3; dz <= 4; dz++)
            for (int dx = 3; dx <= 4; dx++)
                assertEquals(GROUND, field.height(m + dx, m + dz), 1e-6F, "the trunk");
    }

    @Test
    void aCliffKeepsItsHeight() {
        // East of x = 2 the ground is 6 blocks higher: a cliff, not an obstacle
        var ground = new Ground(Integer.MAX_VALUE);
        for (int x = 2; x <= 20; x++)
            for (int z = -20; z <= 20; z++)
                ground.column(x, z, GROUND + 6);
        var field = new GroundFogField(8);
        field.update(ground, 0.5, 0.5, Float.NaN);
        int m = middle(field);
        assertEquals(GROUND, field.height(m + 1, m), 1e-6F, "below the cliff");
        assertEquals(GROUND + 6, field.height(m + 2, m), 1e-6F, "the top of the cliff, at its edge");
    }

    @Test
    void theGroundTheMistIsShapedByChangesGraduallyAtAStep() {
        // Regression: the mist changed thickness in a line at a column's edge. Smoothed, the ground under it rises
        // over a few blocks at a step, and stays within a block or so of the ground itself, so it doesn't climb a cliff
        var ground = new Ground(Integer.MAX_VALUE);
        for (int x = 2; x <= 20; x++)
            for (int z = -20; z <= 20; z++)
                ground.column(x, z, GROUND + 6);
        var field = new GroundFogField(8);
        field.update(ground, 0.5, 0.5, Float.NaN);
        int m = middle(field);
        float last = Float.NEGATIVE_INFINITY;
        for (int i = m - 3; i <= m + 5; i++) {
            float smooth = field.smoothHeight(i, m);
            assertTrue(smooth >= last, "rising at " + i);
            assertTrue(smooth <= field.height(i, m) + GroundFogField.SMOOTH_ABOVE + 1e-5F, "not far above the ground at " + i);
            assertTrue(smooth >= field.height(i, m) - GroundFogField.SMOOTH_BELOW - 1e-5F, "not far below the ground at " + i);
            last = smooth;
        }
        assertEquals(GROUND, field.smoothHeight(m - 3, m), 1e-5F, "flat well away from the step");
        assertTrue(field.smoothHeight(m + 1, m) > GROUND, "banked up at the foot");
    }

    @Test
    void theMistThinsAtABiomesEdge() {
        // Foggy west of x = 0, clear east of it
        var field = new GroundFogField(8);
        field.update(new Ground(0), 0.5, 0.5, Float.NaN);
        int m = middle(field);
        assertEquals(0.8F, field.density(m - 3, m), 1e-6F);
        assertEquals(0F, field.density(m + 3, m), 1e-6F);
        // The columns either side of the edge are blended with each other
        float west = field.density(m - 1, m), east = field.density(m, m);
        assertTrue(west > 0F && west < 0.8F, "blended: " + west);
        assertTrue(east > 0F && east < 0.8F, "blended: " + east);
    }

    @Test
    void thereIsNoMistOutsideTheArea() {
        // The thinning toward the edge is the shader's, from where the camera is exactly; the cells only stop at the
        // area's circle, so they stay the same as the camera moves within a block
        var field = new GroundFogField(16);
        field.update(new Ground(Integer.MAX_VALUE), 0.5, 0.5, Float.NaN);
        int m = middle(field);
        assertEquals(0.8F, field.density(m, m), 1e-6F, "thick in the middle");
        assertEquals(0.8F, field.density(0, m), 1e-6F, "at the edge of the circle");
        assertEquals(0F, field.density(0, 0), 1e-6F, "none in the corners");
        assertEquals(0.8F, field.peak(), 1e-6F);
        assertFalse(field.isEmpty());
    }

    @Test
    void theCellsAreOnlyWorkedOutAgainWhenSomethingChanges() {
        // The cost per tick came from working out every cell again each tick
        var ground = new Ground(Integer.MAX_VALUE);
        var field = new GroundFogField(8);
        field.update(ground, 0.5, 0.5, Float.NaN);
        int version = field.version();

        for (int t = 0; t < GroundFogField.REFRESH_TICKS; t++)
            field.update(ground, 0.5 + 0.4 * t / GroundFogField.REFRESH_TICKS, 0.5, Float.NaN);
        assertEquals(version, field.version(), "staying in the same block, with nothing changed");

        field.update(ground, 1.5, 0.5, Float.NaN);
        assertNotEquals(version, field.version(), "into the next block");
        version = field.version();

        // A block placed
        ground.column(2, 0, GROUND + 1);
        field.columnChanged(2, 0);
        field.update(ground, 1.5, 0.5, Float.NaN);
        assertNotEquals(version, field.version(), "a column changed");
        assertEquals(GROUND + 1, field.height(middle(field) + 1, middle(field)), 1e-6F);

        // Light changed, as from a torch placed nearby
        version = field.version();
        ground.light = 0xF000E0;
        for (int t = 0; t < GroundFogField.REFRESH_TICKS && field.version() == version; t++)
            field.update(ground, 1.5, 0.5, Float.NaN);
        assertNotEquals(version, field.version(), "the light changed");

        version = field.version();
        field.update(ground, 1.5, 0.5, 0.5F);
        assertNotEquals(version, field.version(), "a thickness set");
        version = field.version();
        field.clear();
        assertNotEquals(version, field.version(), "cleared");
    }

    @Test
    void aForcedThicknessIsUsedEverywhere() {
        var ground = new Ground(Integer.MIN_VALUE);
        var field = new GroundFogField(8);
        field.update(ground, 0.5, 0.5, Float.NaN);
        assertTrue(field.isEmpty(), "no fog in these biomes");
        field.update(ground, 0.5, 0.5, 0.5F);
        int m = middle(field);
        assertEquals(0.5F, field.density(m, m), 1e-6F);
        assertFalse(field.isEmpty());
    }

    @Test
    void onlyColumnsComingIntoTheAreaAreLookedAt() {
        var ground = new Ground(Integer.MAX_VALUE);
        var field = new GroundFogField(8);
        int side = field.size();
        field.update(ground, 0.5, 0.5, Float.NaN);
        assertEquals(side * side, ground.looks, "all of them the first time");

        // A block east: a new column along the east side, and the few rows due a fresh look
        ground.looks = 0;
        field.update(ground, 1.5, 0.5, Float.NaN);
        int rowsPerTick = (int) Math.ceil(side / (float) GroundFogField.REFRESH_TICKS);
        assertTrue(ground.looks <= side + rowsPerTick * side, "looked at " + ground.looks);
        assertTrue(ground.looks >= side, "the new side");
    }

    @Test
    void onlyTheLightIsReadAgainNowAndThen() {
        // Looking at a column (down through leaves to the ground, its biome) costs; it is only done when something in
        // it changed. Its light is cheap, and spreads from a change into columns that didn't, so it is read again
        var ground = new Ground(Integer.MAX_VALUE);
        var field = new GroundFogField(8);
        field.update(ground, 0.5, 0.5, Float.NaN);
        ground.looks = 0;
        ground.lightReads = 0;
        for (int t = 0; t < GroundFogField.REFRESH_TICKS; t++)
            field.update(ground, 0.5, 0.5, Float.NaN);
        assertEquals(0, ground.looks, "no column looked at, staying put");
        assertTrue(ground.lightReads >= field.size() * field.size(), "every column's light read");
    }

    @Test
    void theLightIsReadAgainWhereTheLightEngineChangedIt() {
        // Regression: a torch's light reached the mist up to two seconds late, waiting for its row's turn
        var ground = new Ground(Integer.MAX_VALUE);
        var field = new GroundFogField(8);
        field.update(ground, 0.5, 0.5, Float.NaN);
        int m = middle(field);
        int version = field.version();

        // A section above the ground: the light read just above the ground isn't in it
        ground.lightReads = 0;
        field.lightChanged(0, (GROUND >> 4) + 1, 0);
        field.update(ground, 0.5, 0.5, Float.NaN);
        int rowsPerTick = (int) Math.ceil(field.size() / (float) GroundFogField.REFRESH_TICKS);
        assertEquals(rowsPerTick * field.size(), ground.lightReads, "only the rolling refresh");

        // The ground's section: its columns in the area, x and z 0 to 8 of the area's -8 to 8, beside the refresh
        ground.light = 0xF000E0;
        ground.lightReads = 0;
        field.lightChanged(0, GROUND >> 4, 0);
        field.update(ground, 0.5, 0.5, Float.NaN);
        assertTrue(ground.lightReads >= 9 * 9 && ground.lightReads <= 9 * 9 + rowsPerTick * field.size(),
                "read " + ground.lightReads);
        assertEquals(0xF000E0, field.light(m + 1, m + 1));
        assertNotEquals(version, field.version(), "worked out again with the new light");
    }

    @Test
    void onlyTheAreasPartOfASectionIsLookedAt() {
        // Light and chunk changes only look at the columns of theirs that are in the area: the section west and north of
        // the middle, x and z -16 to -1, has x and z -8 to -1 of the area's -8 to 8
        var ground = new Ground(Integer.MAX_VALUE);
        var field = new GroundFogField(8);
        field.update(ground, 0.5, 0.5, Float.NaN);
        int m = middle(field);

        ground.light = 0xF000E0;
        ground.lightReads = 0;
        field.lightChanged(-1, GROUND >> 4, -1);
        // Well away: nothing
        field.lightChanged(40, GROUND >> 4, 40);
        field.update(ground, 0.5, 0.5, Float.NaN);
        int rowsPerTick = (int) Math.ceil(field.size() / (float) GroundFogField.REFRESH_TICKS);
        assertTrue(ground.lightReads >= 8 * 8 && ground.lightReads <= 8 * 8 + rowsPerTick * field.size(),
                "read " + ground.lightReads);
        assertEquals(0xF000E0, field.light(m - 1, m - 1));
        assertEquals(0xF000E0, field.light(m - 8, m - 8));

        ground.looks = 0;
        field.chunkChanged(-1, -1);
        field.chunkChanged(40, 40);
        field.update(ground, 0.5, 0.5, Float.NaN);
        assertEquals(8 * 8, ground.looks);

        // Cleared, nothing is in the area until the next update, which looks at every column anyway
        field.clear();
        field.lightChanged(0, GROUND >> 4, 0);
        field.chunkChanged(0, 0);
        ground.looks = 0;
        field.update(ground, 0.5, 0.5, Float.NaN);
        assertEquals(field.size() * field.size(), ground.looks);
    }

    @Test
    void changedColumnsAreLookedAtAgain() {
        var ground = new Ground(Integer.MAX_VALUE);
        var field = new GroundFogField(8);
        field.update(ground, 0.5, 0.5, Float.NaN);
        int m = middle(field);

        ground.column(1, 1, GROUND + 1);
        ground.looks = 0;
        field.columnChanged(1, 1);
        // Outside the area: nothing to look at
        field.columnChanged(500, 500);
        field.update(ground, 0.5, 0.5, Float.NaN);
        assertEquals(1, ground.looks);
        assertEquals(GROUND + 1, field.height(m + 1, m + 1), 1e-6F);

        // A chunk arriving: its columns in the area, x and z 0 to 8 of the area's -8 to 8
        ground.looks = 0;
        field.chunkChanged(0, 0);
        field.update(ground, 0.5, 0.5, Float.NaN);
        assertEquals(9 * 9, ground.looks);
    }

    @Test
    void theCellsAreAsThePlainWayWorksThemOut() {
        // The field takes shortcuts (most columns can't be obstacles, so aren't sorted; the blur sums rows then
        // columns; the slots are stepped through): on rough ground with posts and a biome's edge, after a move that
        // wraps the cache, its cells are what the plain way gives
        var random = Randomizer.create(1234);
        var ground = new Ground(3);
        for (int x = -24; x <= 24; x++)
            for (int z = -24; z <= 24; z++)
                ground.column(x, z, GROUND + random.nextInt(4) + (random.nextInt(10) == 0 ? 3 + random.nextInt(6) : 0));
        var field = new GroundFogField(12);
        field.update(ground, 0.5, 0.5, Float.NaN);
        field.update(ground, 5.5, -3.5, Float.NaN);

        int size = field.size(), radius = field.radius(), cells = size * size;
        int[] found = new int[cells];
        float[] thickness = new float[cells];
        for (int j = 0; j < size; j++)
            for (int i = 0; i < size; i++) {
                int x = field.cellX(i), z = field.cellZ(j);
                found[j * size + i] = ground.groundHeight(x, z);
                thickness[j * size + i] = ground.density(x, 0, z);
            }

        float[] height = new float[cells];
        for (int k = 0; k < cells; k++)
            height[k] = found[k];
        for (int j = 1; j + 1 < size; j++)
            for (int i = 1; i + 1 < size; i++) {
                int[] around = new int[8];
                int n = 0;
                for (int dj = -1; dj <= 1; dj++)
                    for (int di = -1; di <= 1; di++)
                        if (di != 0 || dj != 0)
                            around[n++] = found[(j + dj) * size + i + di];
                Arrays.sort(around);
                float middle = (around[3] + around[4]) * 0.5F;
                if (found[j * size + i] > middle + GroundFogField.OBSTACLE_HEIGHT)
                    height[j * size + i] = middle;
            }
        float[] smooth = plainBlur(plainBlur(height, size), size);
        for (int k = 0; k < cells; k++)
            smooth[k] = Math.clamp(smooth[k], height[k] - GroundFogField.SMOOTH_BELOW, height[k] + GroundFogField.SMOOTH_ABOVE);

        int obstacles = 0;
        for (int j = 0; j < size; j++)
            for (int i = 0; i < size; i++) {
                int k = j * size + i;
                if (height[k] != found[k])
                    obstacles++;
                float density = 0F;
                int dx = i - radius, dz = j - radius;
                if (dx * dx + dz * dz <= radius * radius) {
                    float sum = 0F;
                    int count = 0;
                    for (int dj = -1; dj <= 1; dj++)
                        for (int di = -1; di <= 1; di++) {
                            int ni = i + di, nj = j + dj;
                            if (ni >= 0 && ni < size && nj >= 0 && nj < size
                                    && Math.abs(height[nj * size + ni] - height[k]) <= GroundFogField.SAME_LEVEL) {
                                sum += thickness[nj * size + ni];
                                count++;
                            }
                        }
                    density = sum / count;
                }
                String at = "cell " + i + ", " + j;
                assertEquals(height[k], field.height(i, j), 1e-6F, at);
                assertEquals(smooth[k], field.smoothHeight(i, j), 1e-4F, at);
                assertEquals(density, field.density(i, j), 1e-6F, at);
            }
        assertTrue(obstacles > 0, "the ground has posts standing in the mist");
    }

    /**
     * The mean of the nine around each cell, as many as are in the area, added up one by one
     */
    private static float[] plainBlur(float[] from, int size) {
        float[] to = new float[from.length];
        for (int j = 0; j < size; j++)
            for (int i = 0; i < size; i++) {
                float sum = 0F;
                int count = 0;
                for (int dj = -1; dj <= 1; dj++)
                    for (int di = -1; di <= 1; di++) {
                        int ni = i + di, nj = j + dj;
                        if (ni >= 0 && ni < size && nj >= 0 && nj < size) {
                            sum += from[nj * size + ni];
                            count++;
                        }
                    }
                to[j * size + i] = sum / count;
            }
        return to;
    }
}

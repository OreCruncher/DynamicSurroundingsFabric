package org.orecruncher.dsurround.effects.groundfog;

import org.junit.jupiter.api.Test;

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
            return 0xF000F0;
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
    void theMistFadesOutAtTheEdgeOfTheArea() {
        var field = new GroundFogField(16);
        field.update(new Ground(Integer.MAX_VALUE), 0.5, 0.5, Float.NaN);
        int m = middle(field);
        assertEquals(0.8F, field.density(m, m), 1e-6F, "thick in the middle");
        assertEquals(0F, field.density(0, m), 1e-6F, "none at the edge");
        assertEquals(0F, field.density(0, 0), 1e-6F, "none in the corners");
        assertEquals(0.8F, field.peak(), 1e-6F);
        assertFalse(field.isEmpty());
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
    void everyColumnIsLookedAtAgainNowAndThen() {
        var ground = new Ground(Integer.MAX_VALUE);
        var field = new GroundFogField(8);
        field.update(ground, 0.5, 0.5, Float.NaN);
        ground.looks = 0;
        for (int t = 0; t < GroundFogField.REFRESH_TICKS; t++)
            field.update(ground, 0.5, 0.5, Float.NaN);
        assertTrue(ground.looks >= field.size() * field.size(), "every column, staying put");
    }
}

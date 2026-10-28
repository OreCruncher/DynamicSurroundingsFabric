package org.orecruncher.dsurround.effects.clouds;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.runtime.weather.CloudCover;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for laying the clouds out as columns of blocks.
 */
public class CloudFieldTests {

    private static final int RADIUS = 20;
    private static final long AFTERNOON = 8000;

    private static CloudField fieldFor(CloudCover cover, int centerX, int centerZ, double time) {
        var field = new CloudField();
        field.update(cover, centerX, centerZ, RADIUS, time, 1F, 0F);
        return field;
    }

    @Test
    void aClearSkyHasNone() {
        var field = fieldFor(CloudCover.CLEAR, 0, 0, 0);
        assertTrue(field.isEmpty());
        assertEquals(0, field.count(CloudField.Layer.LOW));
        assertEquals(0, field.count(CloudField.Layer.HIGH));
    }

    @Test
    void coversAboutTheShareItShould() {
        var cover = new CloudCover(0.4F, 0F, CloudCover.FLAT, CloudCover.FLAT, 0F, 0F);
        var field = fieldFor(cover, 1000, -500, 3);
        int cells = field.size() * field.size();
        float share = field.count(CloudField.Layer.LOW) / (float) cells;
        assertEquals(0.4F, share, 0.15F);
    }

    @Test
    void basesAreFlatAndHeightsComeInSteps() {
        var cover = CloudCover.of(0F, 0F, AFTERNOON, 1F, 1F);
        var field = fieldFor(cover, 0, 0, 0);
        assertFalse(field.isEmpty());
        boolean taller = false;
        for (int j = 0; j < field.size(); j++)
            for (int i = 0; i < field.size(); i++) {
                if (!field.has(CloudField.Layer.LOW, i, j))
                    continue;
                assertEquals(0, field.base(CloudField.Layer.LOW, i, j), "fair weather bases are where vanilla's are");
                int thickness = field.top(CloudField.Layer.LOW, i, j) - field.base(CloudField.Layer.LOW, i, j);
                assertEquals(0, thickness % CloudField.STEP, "in steps");
                assertTrue(thickness >= CloudField.STEP && thickness <= cover.maxThickness() + CloudField.STEP);
                taller |= thickness > CloudField.STEP * 2;
            }
        assertTrue(taller, "a wet afternoon builds some up");
    }

    @Test
    void aStormsDeckIsLowThickAndAlmostUnbroken() {
        var cover = CloudCover.of(1F, 0F, AFTERNOON, 0.5F, 0.5F);
        var field = fieldFor(cover, 0, 0, 0);
        int cells = field.size() * field.size();
        assertTrue(field.count(CloudField.Layer.LOW) > cells * 0.9F, "closed over");
        assertTrue(field.lowestBase() <= -cover.baseDrop() + 1, "lowered: " + field.lowestBase());
        for (int j = 0; j < field.size(); j++)
            for (int i = 0; i < field.size(); i++)
                if (field.has(CloudField.Layer.LOW, i, j))
                    assertTrue(field.top(CloudField.Layer.LOW, i, j) - field.base(CloudField.Layer.LOW, i, j) >= cover.minThickness() - CloudField.STEP / 2F);
        assertEquals(0, field.count(CloudField.Layer.HIGH), "no streaks seen above it");
    }

    @Test
    void aCellIsTheSameWhereverItIsSeenFrom() {
        var cover = CloudCover.of(0.3F, 0F, AFTERNOON, 0.7F, 0.6F);
        var a = fieldFor(cover, 0, 0, 5);
        var b = fieldFor(cover, 7, -4, 5);
        // Cell (10, 10) of a is cloud space cell (10 - RADIUS, 10 - RADIUS); in b it is 7 and 4 cells over
        for (var layer : CloudField.Layer.values())
            for (int j = 4; j < 30; j++)
                for (int i = 7; i < 30; i++) {
                    assertEquals(a.base(layer, i, j), b.base(layer, i - 7, j + 4));
                    assertEquals(a.top(layer, i, j), b.top(layer, i - 7, j + 4));
                }
    }

    @Test
    void changesSlowlyOverTime() {
        var cover = CloudCover.of(0F, 0F, AFTERNOON, 0.7F, 0.6F);
        var before = fieldFor(cover, 0, 0, 1.0);
        var after = fieldFor(cover, 0, 0, 1.0 + 20 * (1D / 3000D));
        int differ = 0;
        for (int j = 0; j < before.size(); j++)
            for (int i = 0; i < before.size(); i++)
                if (before.top(CloudField.Layer.LOW, i, j) != after.top(CloudField.Layer.LOW, i, j)
                        || before.has(CloudField.Layer.LOW, i, j) != after.has(CloudField.Layer.LOW, i, j))
                    differ++;
        int cells = before.size() * before.size();
        assertTrue(differ < cells / 20, "a second changes a few cells, not most: " + differ);
    }

    @Test
    void updateSaysWhetherAnythingChanged() {
        var cover = CloudCover.of(0F, 0F, AFTERNOON, 0.7F, 0.6F);
        var field = new CloudField();
        assertTrue(field.update(cover, 0, 0, RADIUS, 0, 1F, 0F));
        int version = field.version();
        assertFalse(field.update(cover, 0, 0, RADIUS, 0, 1F, 0F), "the same again");
        assertEquals(version, field.version());
        assertTrue(field.update(cover, 1, 0, RADIUS, 0, 1F, 0F), "moved a cell");
        assertTrue(field.version() > version);
        field.clear();
        assertTrue(field.isEmpty());
    }

    @Test
    void streaksStayAboveTheTowers() {
        var cover = new CloudCover(0.6F, 0F, CloudCover.FLAT, 120F, 1F, 0F);
        var field = fieldFor(cover, 0, 0, 0);
        assertTrue(field.count(CloudField.Layer.HIGH) > 0);
        for (int j = 0; j < field.size(); j++)
            for (int i = 0; i < field.size(); i++)
                if (field.has(CloudField.Layer.HIGH, i, j)) {
                    assertEquals(CloudField.HIGH, field.base(CloudField.Layer.HIGH, i, j));
                    assertTrue(field.top(CloudField.Layer.LOW, i, j) < CloudField.HIGH, "a tower through a streak");
                }
    }

    @Test
    void eachUpdateChangesOnlyTheCellsWhoseTurnItIs() {
        var cover = CloudCover.of(0.2F, 0F, AFTERNOON, 0.7F, 0.6F);
        var field = fieldFor(cover, 0, 0, 0);
        var before = copy(field);
        // Much later: most cells would change, but only those of turn 3 do
        field.update(cover, 0, 0, RADIUS, 50, 1F, 0F, 1000, 3, 20);
        int changed = 0;
        for (int j = 0; j < field.size(); j++)
            for (int i = 0; i < field.size(); i++) {
                int cx = field.originX() + i;
                int cz = field.originZ() + j;
                boolean same = before[0][i + j * field.size()] == field.top(CloudField.Layer.LOW, i, j)
                        && before[1][i + j * field.size()] == field.top(CloudField.Layer.HIGH, i, j);
                if (!same) {
                    changed++;
                    assertEquals(3, CloudField.turnOf(cx, cz, 20), "a cell changed out of turn");
                }
            }
        assertTrue(changed > 0, "some changed");
    }

    @Test
    void turnsAreSpreadEvenly() {
        int[] counts = new int[20];
        for (int z = -50; z < 50; z++)
            for (int x = -50; x < 50; x++)
                counts[CloudField.turnOf(x, z, 20)]++;
        for (int c : counts)
            assertEquals(500, c, 100);
        assertEquals(0, CloudField.turnOf(5, 7, 1));
    }

    @Test
    void aCellAtTheEdgeOfACloudHoldsItsPlace() {
        // Covered at 50%; then a little less: cells already in a cloud stay
        var field = fieldFor(new CloudCover(0.5F, 0F, 8F, 8F, 0F, 0F), 0, 0, 0);
        int before = field.count(CloudField.Layer.LOW);
        field.update(new CloudCover(0.5F - CloudField.HOLD / 2F, 0F, 8F, 8F, 0F, 0F), 0, 0, RADIUS, 0, 1F, 0F);
        assertEquals(before, field.count(CloudField.Layer.LOW));
        // Well past it, they go
        field.update(new CloudCover(0.3F, 0F, 8F, 8F, 0F, 0F), 0, 0, RADIUS, 0, 1F, 0F);
        assertTrue(field.count(CloudField.Layer.LOW) < before);
    }

    @Test
    void aHeightHoldsUntilTheNoiseIsClearlyAStepAway() {
        // Thickness for every cell exactly 10 blocks: 2.5 steps, rounding up to 3
        var field = fieldFor(new CloudCover(1F, 0F, 10F, 10F, 0F, 0F), 0, 0, 0);
        assertEquals(3 * CloudField.STEP, field.top(CloudField.Layer.LOW, 5, 5));
        // A little thinner (2.4 steps) would round to 2, but is held
        field.update(new CloudCover(1F, 0F, 9.6F, 9.6F, 0F, 0F), 0, 0, RADIUS, 0, 1F, 0F);
        assertEquals(3 * CloudField.STEP, field.top(CloudField.Layer.LOW, 5, 5));
        // Clearly 2 steps: changes
        field.update(new CloudCover(1F, 0F, 8F, 8F, 0F, 0F), 0, 0, RADIUS, 0, 1F, 0F);
        assertEquals(2 * CloudField.STEP, field.top(CloudField.Layer.LOW, 5, 5));
    }

    @Test
    void movingKeepsEachCellsColumn() {
        var cover = CloudCover.of(0.2F, 0F, AFTERNOON, 0.7F, 0.6F);
        var field = fieldFor(cover, 0, 0, 0);
        var before = copy(field);
        // Moved 3 cells east and 2 south, much later, on a turn: the cells it already had keep their columns
        field.update(cover, 3, 2, RADIUS, 50, 1F, 0F, 1000, 7, 20);
        int size = field.size();
        for (int j = 0; j < size - 2; j++)
            for (int i = 0; i < size - 3; i++) {
                int cx = field.originX() + i;
                int cz = field.originZ() + j;
                if (CloudField.turnOf(cx, cz, 20) == 7)
                    continue;
                assertEquals(before[0][(i + 3) + (j + 2) * size], field.top(CloudField.Layer.LOW, i, j));
            }
        // The cells new to it, along the far edges, were worked out
        var fresh = fieldFor(cover, 3, 2, 50);
        assertEquals(fresh.top(CloudField.Layer.LOW, size - 1, size - 1), field.top(CloudField.Layer.LOW, size - 1, size - 1));
    }

    private static int[][] copy(CloudField field) {
        int size = field.size();
        var tops = new int[2][size * size];
        for (int j = 0; j < size; j++)
            for (int i = 0; i < size; i++) {
                tops[0][i + j * size] = field.top(CloudField.Layer.LOW, i, j);
                tops[1][i + j * size] = field.top(CloudField.Layer.HIGH, i, j);
            }
        return tops;
    }

    @Test
    void eachCellFollowsItsOwnCover() {
        // Overcast to the west of cloud space cell 0, clear to the east
        var overcast = new CloudCover(1F, 0F, 8F, 8F, 0F, 0F);
        CloudField.Cover covers = (cx, cz) -> cx < 0 ? overcast : CloudCover.CLEAR;
        var field = new CloudField();
        field.update(covers, 0, 0, RADIUS, 0, 1F, 0F, 0, 0, 1);
        for (int j = 0; j < field.size(); j++)
            for (int i = 0; i < field.size(); i++) {
                boolean west = field.originX() + i < 0;
                assertEquals(west, field.has(CloudField.Layer.LOW, i, j), "cell " + (field.originX() + i));
            }
    }

    /**
     * Covers that are an estimate (overcast) until told they are known, then clear
     */
    private static final class Loading implements CloudField.Cover {
        boolean loaded;

        @Override
        public CloudCover at(int cx, int cz) {
            return this.loaded ? CloudCover.CLEAR : new CloudCover(1F, 0F, 8F, 8F, 0F, 0F);
        }

        @Override
        public boolean isKnown(int cx, int cz) {
            return this.loaded;
        }
    }

    @Test
    void anEstimatedCellIsWorkedOutAgainAsSoonAsItIsKnown() {
        var covers = new Loading();
        var field = new CloudField();
        field.update(covers, 0, 0, RADIUS, 0, 1F, 0F, 0, 0, 1);
        int cells = field.size() * field.size();
        assertEquals(cells, field.count(CloudField.Layer.LOW), "all estimated overcast");

        // Not known yet: only each cell's turn comes round, with the same estimate, so nothing changes
        field.update(covers, 0, 0, RADIUS, 0, 1F, 0F, 100, 0, 20);
        assertEquals(cells, field.count(CloudField.Layer.LOW));

        // Known: every cell at once, whatever its turn, and none held by the estimate
        covers.loaded = true;
        assertTrue(field.update(covers, 0, 0, RADIUS, 0, 1F, 0F, 101, 0, 20));
        assertEquals(0, field.count(CloudField.Layer.LOW));
        // Fading out from the estimate, not gone at once
        assertTrue(field.isFading(0, 0));
    }

    @Test
    void aKnownCellWaitsForItsTurn() {
        var covers = new Loading();
        covers.loaded = true;
        var field = new CloudField();
        var overcast = new CloudCover(1F, 0F, 8F, 8F, 0F, 0F);
        field.update(overcast, 0, 0, RADIUS, 0, 1F, 0F, 0, 0, 1);
        int cells = field.size() * field.size();
        // Clear now, on turn 3 of 20: only that turn's cells change
        field.update(covers, 0, 0, RADIUS, 0, 1F, 0F, 100, 3, 20);
        int left = field.count(CloudField.Layer.LOW);
        assertTrue(left > cells * 0.8F && left < cells, "left " + left + " of " + cells);
    }

    @Test
    void outsideTheGridIsEmpty() {
        var field = fieldFor(new CloudCover(1F, 0F, 8F, 8F, 1F, 0F), 0, 0, 0);
        assertFalse(field.has(CloudField.Layer.LOW, -1, 0));
        assertFalse(field.has(CloudField.Layer.LOW, 0, field.size()));
        assertEquals(0, field.top(CloudField.Layer.HIGH, field.size(), 0));
    }
}

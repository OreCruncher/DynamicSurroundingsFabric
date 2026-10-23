package org.orecruncher.dsurround.lib.scanner;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.junit.jupiter.api.Test;

import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the scanner's geometry: the sphere's rows, and the jobs that walk the parts of a sphere entering and
 * leaving range, checked against brute force over every position.
 * <p>
 * The key property for movement: the new sphere's positions are exactly those of the move's job plus those the old
 * sphere already had. That is what lets the scanner process only the blocks entering range when the player moves.
 */
class ScannerGeometryTests {

    private static final int NO_FLOOR = -10_000;
    private static final int NO_CEILING = 10_000;

    // ---- Helpers ---------------------------------------------------------------------------------------------

    /**
     * Every position the job produces, walked section by section. Fails if a position is produced twice, lies
     * outside the section being walked, or if a section's count doesn't match what was walked in it.
     */
    private static LongSet drain(ScanJob job) {
        LongSet points = new LongOpenHashSet();
        var pos = new BlockPos.MutableBlockPos();
        while (!job.isDone()) {
            long section = job.currentSection();
            long before = job.remaining();
            int walked = 0;
            while (job.next(pos)) {
                assertEquals(section, SectionPos.asLong(pos), () -> "position outside its section: " + pos);
                assertTrue(points.add(pos.asLong()), () -> "position produced twice: " + pos);
                walked++;
            }
            assertTrue(walked > 0, "a section with nothing in it was queued");
            job.finishSection();
            assertEquals(before - walked, job.remaining(), "section count differs from what was walked");
        }
        assertEquals(0, job.remaining());
        return points;
    }

    /**
     * Brute force: the positions of {@code sphere} within the heights {@code minY} to {@code maxY} that pass
     * {@code keep}.
     */
    private static LongSet pointsOf(ScanSphere sphere, int minY, int maxY, PositionFilter keep) {
        LongSet points = new LongOpenHashSet();
        int r = sphere.radius();
        for (int y = sphere.y() - r; y <= sphere.y() + r; y++) {
            if (y < minY || y > maxY)
                continue;
            for (int z = sphere.z() - r; z <= sphere.z() + r; z++)
                for (int x = sphere.x() - r; x <= sphere.x() + r; x++)
                    if (sphere.contains(x, y, z) && keep.test(x, y, z))
                        points.add(BlockPos.asLong(x, y, z));
        }
        return points;
    }

    private static LongSet pointsOf(ScanSphere sphere) {
        return pointsOf(sphere, NO_FLOOR, NO_CEILING, (x, y, z) -> true);
    }

    @FunctionalInterface
    private interface PositionFilter {
        boolean test(int x, int y, int z);
    }

    // ---- ScanSphere ------------------------------------------------------------------------------------------

    @Test
    void rowsMatchTheSphere() {
        var sphere = new ScanSphere(5, -3, 17, 9);
        for (int y = -3 - 11; y <= -3 + 11; y++)
            for (int z = 17 - 11; z <= 17 + 11; z++) {
                int w = sphere.halfWidth(y, z);
                for (int x = 5 - 11; x <= 5 + 11; x++)
                    assertEquals(sphere.contains(x, y, z), w >= 0 && Math.abs(x - 5) <= w,
                            "row (%d, %d) at x %d".formatted(y, z, x));
            }
    }

    @Test
    void volumeCountsEveryPosition() {
        for (int r : new int[]{0, 1, 2, 7, 16})
            assertEquals(pointsOf(new ScanSphere(0, 0, 0, r)).size(), ScanSphere.volumeOf(r), "radius " + r);
    }

    @Test
    void sphereIsAboutHalfTheCube() {
        // What a longer range costs: a 48 sphere is under twice a 32 cube
        long sphere48 = ScanSphere.volumeOf(48);
        long cube32 = 65L * 65 * 65;
        assertEquals(4.0 / 3.0 * Math.PI * 48 * 48 * 48, sphere48, sphere48 * 0.01);
        assertTrue(sphere48 < 2 * cube32);
    }

    @Test
    void overlapIsTwoRadii() {
        var sphere = new ScanSphere(0, 0, 0, 10);
        assertTrue(sphere.overlaps(new ScanSphere(20, 0, 0, 10)));
        assertFalse(sphere.overlaps(new ScanSphere(21, 0, 0, 10)));
        assertFalse(sphere.overlaps(new ScanSphere(15, 15, 0, 10)));
    }

    // ---- ScanJob ---------------------------------------------------------------------------------------------

    @Test
    void fullScanVisitsTheWholeSphere() {
        var sphere = new ScanSphere(-7, 70, 22, 20);
        var job = ScanJob.full(sphere, NO_FLOOR, NO_CEILING);
        assertEquals(ScanSphere.volumeOf(20), job.remaining());
        assertEquals(pointsOf(sphere), drain(job));
    }

    @Test
    void fullScanStaysInsideTheWorld() {
        var sphere = new ScanSphere(3, -60, 3, 12);
        var job = ScanJob.full(sphere, -64, 319);
        assertEquals(pointsOf(sphere, -64, 319, (x, y, z) -> true), drain(job));
    }

    @Test
    void moveVisitsExactlyWhatEnteredRange() {
        var from = new ScanSphere(100, 64, -40, 12);
        int[][] moves = {
                {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
                {1, 1, 0}, {-1, 0, 1}, {1, -1, -1}, {3, 0, 2}, {-5, 4, 7}, {11, 0, 0}, {0, 23, 0}, {24, 0, 0}
        };
        for (int[] m : moves) {
            var to = new ScanSphere(from.x() + m[0], from.y() + m[1], from.z() + m[2], from.radius());
            var entering = drain(ScanJob.difference(false, to, from, NO_FLOOR, NO_CEILING));
            var expected = pointsOf(to, NO_FLOOR, NO_CEILING, (x, y, z) -> !from.contains(x, y, z));
            assertEquals(expected, entering, () -> "move by " + m[0] + ", " + m[1] + ", " + m[2]);

            var leaving = ScanJob.difference(true, from, to, NO_FLOOR, NO_CEILING);
            assertTrue(leaving.isUnscan());
            assertEquals(pointsOf(from, NO_FLOOR, NO_CEILING, (x, y, z) -> !to.contains(x, y, z)), drain(leaving));
        }
    }

    @Test
    void oneStepTouchesOnlyAShell() {
        var from = new ScanSphere(0, 0, 0, 48);
        var to = new ScanSphere(1, 0, 0, 48);
        var job = ScanJob.difference(false, to, from, NO_FLOOR, NO_CEILING);
        // One block per row of the leading half: the sphere's cross-section, about pi * r^2
        assertEquals(Math.PI * 48 * 48, job.remaining(), Math.PI * 48 * 48 * 0.02);
    }

    @Test
    void moveAgainstTheBuildLimit() {
        var from = new ScanSphere(0, 310, 0, 12);
        var to = new ScanSphere(0, 309, 1, 12);
        var entering = drain(ScanJob.difference(false, to, from, -64, 319));
        assertEquals(pointsOf(to, -64, 319, (x, y, z) -> !from.contains(x, y, z)), entering);
    }

    @Test
    void columnIsTheSphereWithinIt() {
        var sphere = new ScanSphere(10, 64, 10, 20);
        var job = ScanJob.column(sphere, NO_FLOOR, NO_CEILING, 15, 32, -1, 16);
        IntPredicate inX = x -> x >= 15 && x <= 32;
        IntPredicate inZ = z -> z >= -1 && z <= 16;
        assertEquals(pointsOf(sphere, NO_FLOOR, NO_CEILING, (x, y, z) -> inX.test(x) && inZ.test(z)), drain(job));
    }

    @Test
    void columnOutsideTheSphereHasNothing() {
        var sphere = new ScanSphere(0, 64, 0, 20);
        assertTrue(ScanJob.column(sphere, NO_FLOOR, NO_CEILING, 40, 55, 40, 55).isDone());
        // Inside the sphere's box but past its surface
        var corner = ScanJob.column(sphere, NO_FLOOR, NO_CEILING, 16, 20, 16, 20);
        assertTrue(corner.isDone());
        assertEquals(0, corner.remaining());
    }

    @Test
    void sectionsAreVisitedNearestFirst() {
        var sphere = new ScanSphere(37, 80, -5, 40);
        var job = ScanJob.full(sphere, NO_FLOOR, NO_CEILING);
        double last = -1;
        while (!job.isDone()) {
            long section = job.currentSection();
            double dx = SectionPos.sectionToBlockCoord(SectionPos.x(section)) + 7.5 - sphere.x();
            double dy = SectionPos.sectionToBlockCoord(SectionPos.y(section)) + 7.5 - sphere.y();
            double dz = SectionPos.sectionToBlockCoord(SectionPos.z(section)) + 7.5 - sphere.z();
            double distance = dx * dx + dy * dy + dz * dz;
            assertTrue(distance >= last, "section visited out of order");
            last = distance;
            job.finishSection();
        }
    }

    @Test
    void jobResumesPartWayThroughASection() {
        var sphere = new ScanSphere(8, 8, 8, 5);
        var job = ScanJob.full(sphere, NO_FLOOR, NO_CEILING);
        var pos = new BlockPos.MutableBlockPos();

        // Read a few points, as if the tick's budget ran out, then carry on
        LongSet points = new LongOpenHashSet();
        for (int i = 0; i < 5; i++) {
            assertTrue(job.next(pos));
            points.add(pos.asLong());
        }
        while (job.next(pos))
            assertTrue(points.add(pos.asLong()));
        job.finishSection();

        // The sphere lies within one section
        assertTrue(job.isDone());
        assertEquals(pointsOf(sphere), points);
    }

    @Test
    void skippingASectionCountsItsBlocksAsDone() {
        var sphere = new ScanSphere(16, 8, 8, 3); // straddles two sections along X
        var job = ScanJob.full(sphere, NO_FLOOR, NO_CEILING);
        long total = job.remaining();
        long first = job.currentSection();

        job.finishSection();

        var rest = drain(job);
        assertEquals(total, pointsOf(sphere).size());
        long skipped = pointsOf(sphere).longStream().filter(p -> SectionPos.asLong(BlockPos.of(p)) == first).count();
        assertEquals(total - skipped, rest.size());
    }
}

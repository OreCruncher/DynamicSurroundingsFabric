package org.orecruncher.dsurround.lib.scanner;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the scanner's geometry: walking a cuboid, the slabs entering and leaving range when the volume moves,
 * splitting a volume into chunk sections, and the bookkeeping of a queued job.
 * <p>
 * The key property for movement: for two volumes A and B, the complement of A against their intersection, plus the
 * intersection itself, covers A exactly once. That is what lets the scanner process only the blocks entering and
 * leaving range when the player moves.
 */
class ScannerGeometryTests {

    // Need this to bootstrap the Minecraft environment so tests run
    @BeforeAll
    static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---- Helpers ---------------------------------------------------------------------------------------------

    /**
     * Every point the iterator produces. Fails if any point is produced twice.
     */
    private static LongSet collect(CuboidPointIterator iterator) {
        LongSet points = new LongOpenHashSet();
        var pos = new BlockPos.MutableBlockPos();
        while (iterator.next(pos)) {
            assertTrue(points.add(pos.asLong()), () -> "point produced twice: " + pos);
        }
        return points;
    }

    /**
     * Every point in the boxes. Fails if any point is in more than one box.
     */
    private static LongSet collect(List<BlockBox> boxes) {
        LongSet points = new LongOpenHashSet();
        for (var box : boxes) {
            for (long p : collect(new CuboidPointIterator(box)))
                assertTrue(points.add(p), () -> "point in more than one box: " + BlockPos.of(p) + " in " + boxes);
        }
        return points;
    }

    /**
     * Reference implementation using vanilla's iterator.
     */
    private static LongSet pointsOf(BlockBox box) {
        LongSet points = new LongOpenHashSet();
        for (var pos : BlockPos.betweenClosed(box.min(), box.max()))
            points.add(pos.asLong());
        return points;
    }

    private static BlockBox box(int x0, int y0, int z0, int x1, int y1, int z1) {
        return BlockBox.of(new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1));
    }

    /**
     * The complement of {@code volume} against {@code intersect}, combined with {@code intersect}, must be exactly
     * {@code volume}, with no point in both.
     */
    private static void assertComplementCovers(BlockBox volume, BlockBox intersect) {
        LongSet complement = collect(Cuboid.complement(volume, intersect));
        LongSet inside = pointsOf(intersect);

        for (long p : complement) {
            assertFalse(inside.contains(p), () -> "complement includes intersect point " + BlockPos.of(p)
                    + " for volume " + volume + " and intersect " + intersect);
        }

        LongSet union = new LongOpenHashSet(complement);
        union.addAll(inside);
        assertEquals(pointsOf(volume), union, () -> "complement + intersect != volume for " + volume + ", " + intersect);
    }

    // ---- CuboidPointIterator ---------------------------------------------------------------------------------

    @Test
    void cuboidVisitsEveryPointExactlyOnce() {
        var box = box(-2, -1, 3, 1, 2, 5);

        LongSet points = collect(new CuboidPointIterator(box));

        assertEquals(Cuboid.volumeOf(box), points.size());
        assertEquals(pointsOf(box), points);
    }

    @Test
    void cornersMayBeGivenInEitherOrder() {
        var a = new BlockPos(4, 10, -3);
        var b = new BlockPos(-1, 7, 2);

        assertEquals(collect(new CuboidPointIterator(a, b)), collect(new CuboidPointIterator(b, a)));
    }

    @Test
    void singlePointCuboidProducesOnePoint() {
        var p = new BlockPos(5, 64, -7);

        LongSet points = collect(new CuboidPointIterator(p, p));

        assertEquals(1, points.size());
        assertTrue(points.contains(p.asLong()));
    }

    @Test
    void firstAndLastPointsAreIncluded() {
        // Regression: the old iterator shared BlockPos.betweenClosed's mutable cursor, skipping the first point and
        // producing the last one twice.
        var min = new BlockPos(0, 0, 0);
        var max = new BlockPos(2, 2, 2);

        LongSet points = collect(new CuboidPointIterator(min, max));

        assertTrue(points.contains(min.asLong()));
        assertTrue(points.contains(max.asLong()));
    }

    @Test
    void exhaustedIteratorLeavesTheOutputUnchanged() {
        var it = new CuboidPointIterator(BlockPos.ZERO, BlockPos.ZERO);
        var pos = new BlockPos.MutableBlockPos();

        assertTrue(it.next(pos));
        pos.set(9, 9, 9);
        assertFalse(it.next(pos));
        assertEquals(new BlockPos(9, 9, 9), pos);
    }

    @Test
    void emptyIteratorHasNoPoints() {
        assertFalse(CuboidPointIterator.EMPTY.next(new BlockPos.MutableBlockPos()));
    }

    // ---- Cuboid.complement -----------------------------------------------------------------------------------

    @Test
    void complementOfIdenticalVolumesIsEmpty() {
        var box = box(0, 0, 0, 4, 4, 4);

        assertTrue(Cuboid.complement(box, box).isEmpty());
    }

    @Test
    void oneStepMoveTouchesOnlyTheEdgeLayers() {
        // Regression: the slabs used to include a layer of the intersect, so a one block move scanned two layers
        // and unscanned a layer that was still in range.
        var oldVolume = box(0, 0, 0, 10, 10, 10);
        var newVolume = oldVolume.offset(new Vec3i(1, 0, 0));
        var intersect = Cuboid.intersection(oldVolume, newVolume);
        assertNotNull(intersect);

        LongSet leaving = collect(Cuboid.complement(oldVolume, intersect));
        LongSet entering = collect(Cuboid.complement(newVolume, intersect));

        assertEquals(11 * 11, leaving.size());
        assertEquals(11 * 11, entering.size());
        leaving.forEach(p -> assertEquals(0, BlockPos.getX(p)));
        entering.forEach(p -> assertEquals(11, BlockPos.getX(p)));
    }

    @Test
    void complementCoversEveryMoveExactlyOnce() {
        // Same-size volumes, as the scanner uses, moved by every offset up to beyond their size on each axis
        var oldVolume = box(-3, 60, 7, 2, 64, 13);
        int checked = 0;

        for (int dx = -7; dx <= 7; dx++)
            for (int dy = -6; dy <= 6; dy++)
                for (int dz = -8; dz <= 8; dz++) {
                    var newVolume = oldVolume.offset(new Vec3i(dx, dy, dz));
                    var intersect = Cuboid.intersection(oldVolume, newVolume);
                    if (intersect == null)
                        continue;

                    assertComplementCovers(oldVolume, intersect);
                    assertComplementCovers(newVolume, intersect);
                    checked++;
                }

        assertTrue(checked > 0);
    }

    @Test
    void complementCoversVolumesClampedAtTheBuildLimit() {
        // Near the top or bottom of the world the scanner clamps the volume, so old and new can differ in height
        var oldVolume = box(0, 300, 0, 8, 319, 8);   // clamped at the top
        var newVolume = box(2, 296, -1, 10, 312, 7); // moved down, not clamped
        var intersect = Cuboid.intersection(oldVolume, newVolume);
        assertNotNull(intersect);

        assertComplementCovers(oldVolume, intersect);
        assertComplementCovers(newVolume, intersect);
    }

    // ---- Cuboid.splitBySection -------------------------------------------------------------------------------

    @Test
    void sectionPiecesCoverTheBoxExactlyOnce() {
        // Crosses section boundaries on every axis, including into negative coordinates
        var box = box(-20, -5, -1, 17, 40, 33);

        var pieces = Cuboid.splitBySection(box);

        assertEquals(pointsOf(box), collect(pieces));
    }

    @Test
    void eachSectionPieceLiesWithinOneSection() {
        var box = box(-20, -5, -1, 17, 40, 33);

        for (var piece : Cuboid.splitBySection(box)) {
            assertEquals(SectionPos.of(piece.min()), SectionPos.of(piece.max()), () -> "piece spans sections: " + piece);
        }
    }

    @Test
    void boxInsideOneSectionIsNotSplit() {
        var box = box(1, 2, 3, 14, 15, 12);

        assertEquals(List.of(box), Cuboid.splitBySection(box));
    }

    @Test
    void sectionAlignedBoxSplitsIntoWholeSections() {
        var box = box(0, 0, 0, 31, 15, 47);

        var pieces = Cuboid.splitBySection(box);

        assertEquals(2 * 1 * 3, pieces.size());
        pieces.forEach(p -> assertEquals(16 * 16 * 16, Cuboid.volumeOf(p)));
    }

    // ---- ScanJob ---------------------------------------------------------------------------------------------

    @Test
    void jobVisitsEveryPointAndCountsDown() {
        var region = box(-3, 10, 14, 20, 12, 18);
        var job = new ScanJob(false, region);
        assertEquals(Cuboid.volumeOf(region), job.remaining());

        LongSet points = new LongOpenHashSet();
        var pos = new BlockPos.MutableBlockPos();
        while (!job.isDone()) {
            var points0 = job.points();
            while (points0.next(pos))
                assertTrue(points.add(pos.asLong()));
            job.finishBox();
        }

        assertEquals(pointsOf(region), points);
        assertEquals(0, job.remaining());
        assertNull(job.currentBox());
    }

    @Test
    void jobResumesPartWayThroughABox() {
        var region = box(0, 0, 0, 3, 3, 3);
        var job = new ScanJob(true, region);
        var pos = new BlockPos.MutableBlockPos();

        // Read a few points, as if the tick's budget ran out, then carry on
        LongSet points = new LongOpenHashSet();
        for (int i = 0; i < 5; i++) {
            assertTrue(job.points().next(pos));
            points.add(pos.asLong());
        }
        while (job.points().next(pos))
            assertTrue(points.add(pos.asLong()));

        assertEquals(pointsOf(region), points);
        assertTrue(job.isUnscan());
    }

    @Test
    void skippingABoxCountsItsBlocksAsDone() {
        var region = box(0, 0, 0, 31, 0, 0); // two sections along X
        var job = new ScanJob(false, region);

        job.finishBox();

        assertEquals(16, job.remaining());
        assertEquals(box(16, 0, 0, 31, 0, 0), job.currentBox());
    }
}

package org.orecruncher.dsurround.lib.scanner;

import it.unimi.dsi.fastutil.ints.IntArrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.jetbrains.annotations.Nullable;

/**
 * A queued piece of scanner work: the positions inside one sphere and not inside another (or all of the first), within
 * the world's height and an optional column, whose blocks are to be scanned (they came into range) or unscanned (they
 * left range).
 * <p>
 * Every row of a sphere along X is one span, so a row of the region is at most two spans: the inner sphere's minus
 * the outer's. That keeps a move cheap however the player moves; a step forward touches only a thin shell.
 * <p>
 * The work is grouped by chunk section so the scanner can skip a whole section at once when it is empty or not
 * loaded. Sections are visited nearest the inner sphere's center first, so what is close shows up first. Within a
 * section, X changes fastest, then Z, then Y. A job can be paused partway and resumed on a later tick.
 */
final class ScanJob {

    private static final int NO_LIMIT_LOW = Integer.MIN_VALUE;
    private static final int NO_LIMIT_HIGH = Integer.MAX_VALUE;

    private final boolean unscan;
    private final ScanSphere inside;
    private final @Nullable ScanSphere outside;
    // The region's bounds: the inner sphere's, trimmed to the world's height and the column
    private final int minX, minY, minZ, maxX, maxY, maxZ;

    // The sections holding some of the region, in visiting order, and how many of its blocks each holds
    private final long[] sections;
    private final long[] counts;
    private int index = 0;
    private long remaining;

    // Walking the current section
    private final int[] spans = new int[4];
    private int spanCount;
    private int spanIndex;
    private int x, y, z;
    private boolean rowStarted;

    private ScanJob(final boolean unscan, final ScanSphere inside, final @Nullable ScanSphere outside,
                    final int worldMinY, final int worldMaxY,
                    final int columnMinX, final int columnMaxX, final int columnMinZ, final int columnMaxZ) {
        this.unscan = unscan;
        this.inside = inside;
        this.outside = outside;

        final int r = inside.radius();
        this.minX = Math.max(inside.x() - r, columnMinX);
        this.maxX = Math.min(inside.x() + r, columnMaxX);
        this.minY = Math.max(inside.y() - r, worldMinY);
        this.maxY = Math.min(inside.y() + r, worldMaxY);
        this.minZ = Math.max(inside.z() - r, columnMinZ);
        this.maxZ = Math.min(inside.z() + r, columnMaxZ);

        if (this.minX > this.maxX || this.minY > this.maxY || this.minZ > this.maxZ) {
            this.sections = new long[0];
            this.counts = new long[0];
            return;
        }

        // Count the region's blocks in each section it touches, a row at a time
        final int sx0 = SectionPos.blockToSectionCoord(this.minX);
        final int sy0 = SectionPos.blockToSectionCoord(this.minY);
        final int sz0 = SectionPos.blockToSectionCoord(this.minZ);
        final int nx = SectionPos.blockToSectionCoord(this.maxX) - sx0 + 1;
        final int ny = SectionPos.blockToSectionCoord(this.maxY) - sy0 + 1;
        final int nz = SectionPos.blockToSectionCoord(this.maxZ) - sz0 + 1;
        final long[] grid = new long[nx * ny * nz];
        final int[] rowSpans = new int[4];
        for (int ry = this.minY; ry <= this.maxY; ry++) {
            final int gy = (SectionPos.blockToSectionCoord(ry) - sy0) * nz;
            for (int rz = this.minZ; rz <= this.maxZ; rz++) {
                final int gyz = (gy + SectionPos.blockToSectionCoord(rz) - sz0) * nx;
                final int n = this.rowSpans(ry, rz, this.minX, this.maxX, rowSpans);
                for (int s = 0; s < n; s++) {
                    // Split the span at section boundaries
                    int from = rowSpans[2 * s];
                    final int to = rowSpans[2 * s + 1];
                    while (from <= to) {
                        final int sectionX = SectionPos.blockToSectionCoord(from);
                        final int end = Math.min(to, SectionPos.sectionToBlockCoord(sectionX, 15));
                        grid[gyz + sectionX - sx0] += end - from + 1;
                        from = end + 1;
                    }
                }
            }
        }

        int used = 0;
        for (long count : grid)
            if (count > 0)
                used++;

        final long[] found = new long[used];
        final long[] foundCounts = new long[used];
        final long[] distances = new long[used];
        int i = 0;
        for (int gy = 0; gy < ny; gy++)
            for (int gz = 0; gz < nz; gz++)
                for (int gx = 0; gx < nx; gx++) {
                    final long count = grid[(gy * nz + gz) * nx + gx];
                    if (count == 0)
                        continue;
                    final int sx = sx0 + gx, sy = sy0 + gy, sz = sz0 + gz;
                    found[i] = SectionPos.asLong(sx, sy, sz);
                    foundCounts[i] = count;
                    // From the sphere's center to the section's, doubled to stay whole
                    final long dx = 2L * SectionPos.sectionToBlockCoord(sx) + 15 - 2L * inside.x();
                    final long dy = 2L * SectionPos.sectionToBlockCoord(sy) + 15 - 2L * inside.y();
                    final long dz = 2L * SectionPos.sectionToBlockCoord(sz) + 15 - 2L * inside.z();
                    distances[i] = dx * dx + dy * dy + dz * dz;
                    this.remaining += count;
                    i++;
                }

        final int[] order = new int[used];
        for (int k = 0; k < used; k++)
            order[k] = k;
        IntArrays.quickSort(order, (a, b) -> Long.compare(distances[a], distances[b]));

        this.sections = new long[used];
        this.counts = new long[used];
        for (int k = 0; k < used; k++) {
            this.sections[k] = found[order[k]];
            this.counts[k] = foundCounts[order[k]];
        }
    }

    /**
     * Everything in {@code sphere} within the world's height, scanned.
     */
    static ScanJob full(final ScanSphere sphere, final int worldMinY, final int worldMaxY) {
        return new ScanJob(false, sphere, null, worldMinY, worldMaxY, NO_LIMIT_LOW, NO_LIMIT_HIGH, NO_LIMIT_LOW, NO_LIMIT_HIGH);
    }

    /**
     * What is in {@code inside} but not {@code outside}, within the world's height: for a move, the new sphere
     * against the old one for blocks entering range, or the old against the new for blocks leaving it.
     */
    static ScanJob difference(final boolean unscan, final ScanSphere inside, final ScanSphere outside,
                              final int worldMinY, final int worldMaxY) {
        return new ScanJob(unscan, inside, outside, worldMinY, worldMaxY, NO_LIMIT_LOW, NO_LIMIT_HIGH, NO_LIMIT_LOW, NO_LIMIT_HIGH);
    }

    /**
     * What of {@code sphere} lies in the given column (bounds inclusive) within the world's height, scanned.
     */
    static ScanJob column(final ScanSphere sphere, final int worldMinY, final int worldMaxY,
                          final int minX, final int maxX, final int minZ, final int maxZ) {
        return new ScanJob(false, sphere, null, worldMinY, worldMaxY, minX, maxX, minZ, maxZ);
    }

    /**
     * Writes the spans of the row at {@code (rowY, rowZ)} that are in the region and between {@code low} and
     * {@code high} into {@code out} as (first, last) pairs, in order along X.
     *
     * @return the number of spans: 0, 1 or 2
     */
    private int rowSpans(final int rowY, final int rowZ, final int low, final int high, final int[] out) {
        final int w = this.inside.halfWidth(rowY, rowZ);
        if (w < 0)
            return 0;
        final int a = Math.max(this.inside.x() - w, low);
        final int b = Math.min(this.inside.x() + w, high);
        if (a > b)
            return 0;

        final ScanSphere cutter = this.outside;
        final int cut = cutter == null ? -1 : cutter.halfWidth(rowY, rowZ);
        if (cut < 0) {
            out[0] = a;
            out[1] = b;
            return 1;
        }

        // [a, b] minus [c, d]: what lies before c and what lies after d
        final int c = cutter.x() - cut;
        final int d = cutter.x() + cut;
        int n = 0;
        if (c > a) {
            out[0] = a;
            out[1] = Math.min(b, c - 1);
            n = 1;
        }
        if (d < b) {
            out[2 * n] = Math.max(a, d + 1);
            out[2 * n + 1] = b;
            n++;
        }
        return n;
    }

    /**
     * True if the blocks are leaving range, false if they are coming into range.
     */
    boolean isUnscan() {
        return this.unscan;
    }

    boolean isDone() {
        return this.index >= this.sections.length;
    }

    /**
     * The section being worked on, packed with {@link SectionPos#asLong(int, int, int)}. Only while not done.
     */
    long currentSection() {
        return this.sections[this.index];
    }

    /**
     * Writes the next position of the region in the current section into {@code out}, resuming where the last call
     * left off.
     *
     * @return false once the section has no more ({@code out} is then unchanged)
     */
    boolean next(final BlockPos.MutableBlockPos out) {
        while (true) {
            if (this.spanIndex < this.spanCount) {
                if (this.x <= this.spans[2 * this.spanIndex + 1]) {
                    out.set(this.x++, this.y, this.z);
                    return true;
                }
                if (++this.spanIndex < this.spanCount)
                    this.x = this.spans[2 * this.spanIndex];
                continue;
            }
            if (!this.nextRow())
                return false;
        }
    }

    /**
     * Moves to the next row of the current section and works out its spans.
     */
    private boolean nextRow() {
        final long section = this.sections[this.index];
        final int sy = SectionPos.y(section);
        final int sz = SectionPos.z(section);
        final int lowZ = Math.max(this.minZ, SectionPos.sectionToBlockCoord(sz));
        if (!this.rowStarted) {
            this.rowStarted = true;
            this.y = Math.max(this.minY, SectionPos.sectionToBlockCoord(sy));
            this.z = lowZ;
        } else if (++this.z > Math.min(this.maxZ, SectionPos.sectionToBlockCoord(sz, 15))) {
            this.z = lowZ;
            this.y++;
        }
        if (this.y > Math.min(this.maxY, SectionPos.sectionToBlockCoord(sy, 15))) {
            this.spanCount = 0;
            return false;
        }

        final int sx = SectionPos.x(section);
        this.spanCount = this.rowSpans(this.y, this.z,
                Math.max(this.minX, SectionPos.sectionToBlockCoord(sx)),
                Math.min(this.maxX, SectionPos.sectionToBlockCoord(sx, 15)),
                this.spans);
        this.spanIndex = 0;
        if (this.spanCount > 0)
            this.x = this.spans[0];
        return true;
    }

    /**
     * Moves on to the next section, whether the current one was walked to the end or skipped.
     */
    void finishSection() {
        this.remaining -= this.counts[this.index];
        this.index++;
        this.rowStarted = false;
        this.spanCount = 0;
        this.spanIndex = 0;
    }

    /**
     * Blocks not yet processed. Counts whole sections, so a partly walked section still counts in full.
     */
    long remaining() {
        return this.remaining;
    }
}

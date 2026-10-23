package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.core.BlockPos;

/**
 * The block positions within {@code radius} blocks of a center: those whose squared distance from it, center to
 * center, is at most {@code radius * radius}. Each row of the sphere along X is a single span, which is how the
 * scanner walks it (see {@link #halfWidth}).
 */
public record ScanSphere(int x, int y, int z, int radius) {

    public static ScanSphere around(final BlockPos center, final int radius) {
        return new ScanSphere(center.getX(), center.getY(), center.getZ(), radius);
    }

    public boolean contains(final BlockPos pos) {
        return this.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean contains(final int px, final int py, final int pz) {
        final long dx = px - this.x;
        final long dy = py - this.y;
        final long dz = pz - this.z;
        return dx * dx + dy * dy + dz * dz <= (long) this.radius * this.radius;
    }

    /**
     * How far the row at {@code (rowY, rowZ)} reaches either side of the center along X: the row holds
     * {@code x - halfWidth} to {@code x + halfWidth}. -1 if the row misses the sphere.
     */
    public int halfWidth(final int rowY, final int rowZ) {
        final long dy = rowY - this.y;
        final long dz = rowZ - this.z;
        final long left = (long) this.radius * this.radius - dy * dy - dz * dz;
        // Exact for these magnitudes: a square root is correctly rounded, and no non-square under 2^52 has one
        // close enough to a whole number to round up to it
        return left < 0 ? -1 : (int) Math.sqrt(left);
    }

    /**
     * True if the two spheres share a position (for the same radius: the centers are at most two radii apart).
     */
    public boolean overlaps(final ScanSphere other) {
        final long dx = other.x - this.x;
        final long dy = other.y - this.y;
        final long dz = other.z - this.z;
        final long reach = (long) this.radius + other.radius;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    /**
     * The number of positions in a sphere of {@code radius}.
     */
    public static long volumeOf(final int radius) {
        var sphere = new ScanSphere(0, 0, 0, radius);
        long count = 0;
        for (int dy = -radius; dy <= radius; dy++)
            for (int dz = -radius; dz <= radius; dz++) {
                int w = sphere.halfWidth(dy, dz);
                if (w >= 0)
                    count += 2L * w + 1;
            }
        return count;
    }
}

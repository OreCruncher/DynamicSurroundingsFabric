package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;

/**
 * Visits every position in a cuboid exactly once, bounds inclusive, without allocating: each point is written into
 * a position supplied by the caller. X changes fastest, then Z, then Y, so each
 * horizontal layer is finished before moving up.
 */
public final class CuboidPointIterator {

    /**
     * An iterator with no points.
     */
    static final CuboidPointIterator EMPTY = new CuboidPointIterator();

    private final int minX, minZ;
    private final int maxX, maxY, maxZ;
    private int x, y, z;
    private boolean done;

    private CuboidPointIterator() {
        this.minX = this.minZ = this.maxX = this.maxY = this.maxZ = 0;
        this.done = true;
    }

    public CuboidPointIterator(final BlockBox box) {
        this(box.min(), box.max());
    }

    /**
     * The two corners may be given in either order.
     */
    public CuboidPointIterator(final BlockPos p1, final BlockPos p2) {
        this.minX = Math.min(p1.getX(), p2.getX());
        this.minZ = Math.min(p1.getZ(), p2.getZ());
        this.maxX = Math.max(p1.getX(), p2.getX());
        this.maxY = Math.max(p1.getY(), p2.getY());
        this.maxZ = Math.max(p1.getZ(), p2.getZ());

        this.x = this.minX;
        this.y = Math.min(p1.getY(), p2.getY());
        this.z = this.minZ;
        this.done = false;
    }

    /**
     * Writes the next point into {@code out}.
     *
     * @return true if a point was written, false if there are no more points ({@code out} is then unchanged)
     */
    public boolean next(final BlockPos.MutableBlockPos out) {
        if (this.done)
            return false;

        out.set(this.x, this.y, this.z);

        if (++this.x > this.maxX) {
            this.x = this.minX;
            if (++this.z > this.maxZ) {
                this.z = this.minZ;
                if (++this.y > this.maxY)
                    this.done = true;
            }
        }
        return true;
    }
}

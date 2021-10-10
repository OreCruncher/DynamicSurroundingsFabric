package org.orecruncher.dsurround.lib;

import com.google.common.collect.AbstractIterator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

@SuppressWarnings("unused")
public final class BlockPosUtil {

    public static BlockPos.Mutable setPos( final BlockPos.Mutable pos,
                                           final Vec3d vec) {
        return pos.set(vec.x, vec.y, vec.z);
    }

    public static boolean canFormCuboid( final BlockPos p1,  final BlockPos p2) {
        return !(p1.getX() == p2.getX() || p1.getZ() == p2.getZ() || p1.getY() == p2.getY());
    }

    public static BlockPos createMinPoint( final BlockPos p1,  final BlockPos p2) {
        return new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()),
                Math.min(p1.getZ(), p2.getZ()));
    }

    public static BlockPos createMaxPoint( final BlockPos p1,  final BlockPos p2) {
        return new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()),
                Math.max(p1.getZ(), p2.getZ()));
    }

    /**
     * Determines if the test point is contained within the volume described by two
     * other points. It is expected that the calling routine has ensured that the
     * min/max points are valid. If they are not valid the results will more than
     * likely be erroneous.
     *
     * @param test The point that is being tested
     * @param min  The point describing the minimum vertex of the volume
     * @param max  The point describing the maximum vertex of the volume
     * @return Whether the test point is within the boundaries of the volume,
     * inclusive
     */
    public static boolean contains( final BlockPos test,  final BlockPos min,
                                    final BlockPos max) {
        return test.getX() >= min.getX() && test.getX() <= max.getX()
                && test.getY() >= min.getY() && test.getY() <= max.getY()
                && test.getZ() >= min.getZ() && test.getZ() <= max.getZ();
    }

    /**
     * Determines if the test point is outside the volume described by two other
     * points. It is expected that the calling routine has ensured that the min/max
     * points are valid. If they are not valid the results will more than likely be
     * erroneous.
     *
     * @param test The point that is being tested
     * @param min  The point describing the minimum vertex of the volume
     * @param max  The point describing the maximum vertex of the volume
     * @return Whether the test point is outside the boundaries of the volume,
     * exclusive
     */
    public static boolean notContains( final BlockPos test,  final BlockPos min,
                                       final BlockPos max) {
        return test.getX() < min.getX() || test.getX() > max.getX()
                || test.getY() < min.getY() || test.getY() > max.getY()
                || test.getZ() < min.getZ() || test.getZ() > max.getZ();
    }

    /**
     * Like getAllInBox but changes the order in which the axis is scanned.  Inside a chunk the data is stored as an
     * array.  The iteration favors moving along the x axis, followed by z, and then y.  In general this will cause
     * the chunk array to be scanned in a linear fashion.
     */
    public static Iterable<BlockPos.Mutable> getAllInBoxMutable(final BlockPos from, final BlockPos to) {
        final BlockPos minPos = createMinPoint(from, to);
        final BlockPos maxPos = createMaxPoint(from, to);
        return () -> new AbstractIterator<>() {

            private final int minX = minPos.getX();
            private final int minY = minPos.getY();
            private final int minZ = minPos.getZ();
            private final int maxX = maxPos.getX();
            private final int maxY = maxPos.getY();
            private final int maxZ = maxPos.getZ();

            private BlockPos.Mutable currentPos;
            private int currentX = minX;
            private int currentY = minY;
            private int currentZ = minZ;

            private boolean isEndFinished() {
                return currentX == maxX && currentY == maxY && currentZ == maxZ;
            }

            @Override
            protected BlockPos.Mutable computeNext() {
                if (this.currentPos == null) {
                    this.currentPos = new BlockPos.Mutable(minX, minY, minZ);
                    return this.currentPos;
                } else if (isEndFinished()) {
                    return endOfData();
                } else {

                    if (currentX < maxX) {
                        currentX++;
                    } else if (currentZ < maxZ) {
                        currentX = minX;
                        currentZ++;
                    } else if (currentY < maxY) {
                        currentX = minX;
                        currentZ = minZ;
                        currentY++;
                    }

                    this.currentPos.set(currentX, currentY, currentZ);
                    return this.currentPos;
                }
            }
        };
    }
}
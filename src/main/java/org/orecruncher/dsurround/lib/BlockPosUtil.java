package org.orecruncher.dsurround.lib;

import net.minecraft.util.math.BlockPos;

public final class BlockPosUtil {

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
}
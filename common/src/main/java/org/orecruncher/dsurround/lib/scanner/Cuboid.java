package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class Cuboid {

    public static BlockBox of(BlockPos[] points) {
        return of(points[0], points[1]);
    }

    public static BlockBox of(BlockPos pos1, BlockPos pos2) {
        return BlockBox.of(pos1, pos2);
    }

    public static boolean intersects(BlockBox box1, BlockBox box2) {
        var meMin = box1.min();
        var meMax = box1.max();
        var oMin = box2.min();
        var oMax = box2.max();
        return meMin.getX() <= oMax.getX()
                && meMax.getX() >= oMin.getX()
                && meMin.getY() <= oMax.getY()
                && meMax.getY() >= oMin.getY()
                && meMin.getZ() <= oMax.getZ()
                && meMax.getZ() >= oMin.getZ();
    }

    @Nullable
    public static BlockBox intersection(BlockBox box1, BlockBox box2) {
        if (intersects(box1, box2)) {
            var meMin = box1.min();
            var meMax = box1.max();
            var oMin = box2.min();
            var oMax = box2.max();
            int minX = Math.max(meMin.getX(), oMin.getX());
            int minY = Math.max(meMin.getY(), oMin.getY());
            int minZ = Math.max(meMin.getZ(), oMin.getZ());
            int maxX = Math.min(meMax.getX(), oMax.getX());
            int maxY = Math.min(meMax.getY(), oMax.getY());
            int maxZ = Math.min(meMax.getZ(), oMax.getZ());
            return new BlockBox(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
        }
        return null;
    }
}
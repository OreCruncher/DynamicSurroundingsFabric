package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry helpers for {@link BlockBox}. All bounds are inclusive.
 */
public final class Cuboid {

    private Cuboid() {
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

    /**
     * The number of block positions in the box.
     */
    public static long volumeOf(BlockBox box) {
        return (long) box.sizeX() * box.sizeY() * box.sizeZ();
    }

    /**
     * The part of {@code volume} that is not in {@code intersect}, as up to three non-overlapping boxes (one slab
     * per axis).
     * <p>
     * Assumes {@code intersect} lies within {@code volume} and touches it on at least one side of every axis, which
     * always holds when {@code intersect} is the intersection of {@code volume} with a box of the same size, or
     * with one clamped to the same height limits. For example:
     * <pre>
     *     BlockBox intersect = Cuboid.intersection(oldVolume, newVolume);
     *     List&lt;BlockBox&gt; leaving = Cuboid.complement(oldVolume, intersect);
     *     List&lt;BlockBox&gt; entering = Cuboid.complement(newVolume, intersect);
     * </pre>
     */
    public static List<BlockBox> complement(BlockBox volume, BlockBox intersect) {
        final BlockPos vmax = volume.max();
        final BlockPos imax = intersect.max();
        final BlockPos vmin = volume.min();
        final BlockPos imin = intersect.min();

        var result = new ArrayList<BlockBox>(3);

        // Each slab starts one past the intersect's max, or ends one before its min, so it never includes a layer
        // of the intersect. The X slab spans the volume's full Y and Z; the Y slab is limited to the intersect's X;
        // the Z slab to the intersect's X and Y. Together they cover the rest of the volume exactly once.
        if (vmax.getX() != imax.getX() || vmin.getX() != imin.getX()) {
            if (vmax.getX() > imax.getX())
                result.add(box(imax.getX() + 1, vmin.getY(), vmin.getZ(), vmax.getX(), vmax.getY(), vmax.getZ()));
            else
                result.add(box(vmin.getX(), vmin.getY(), vmin.getZ(), imin.getX() - 1, vmax.getY(), vmax.getZ()));
        }

        if (vmax.getY() != imax.getY() || vmin.getY() != imin.getY()) {
            if (vmax.getY() > imax.getY())
                result.add(box(imin.getX(), imax.getY() + 1, vmin.getZ(), imax.getX(), vmax.getY(), vmax.getZ()));
            else
                result.add(box(imin.getX(), vmin.getY(), vmin.getZ(), imax.getX(), imin.getY() - 1, vmax.getZ()));
        }

        if (vmax.getZ() != imax.getZ() || vmin.getZ() != imin.getZ()) {
            if (vmax.getZ() > imax.getZ())
                result.add(box(imin.getX(), imin.getY(), imax.getZ() + 1, imax.getX(), imax.getY(), vmax.getZ()));
            else
                result.add(box(imin.getX(), imin.getY(), vmin.getZ(), imax.getX(), imax.getY(), imin.getZ() - 1));
        }

        return result;
    }

    /**
     * Splits the box along chunk section boundaries (every 16 blocks on each axis). Each piece lies within a
     * single section, and together they cover the box exactly once. Ordered by Y, then Z, then X.
     */
    public static List<BlockBox> splitBySection(BlockBox box) {
        var min = box.min();
        var max = box.max();
        int minSX = SectionPos.blockToSectionCoord(min.getX());
        int minSY = SectionPos.blockToSectionCoord(min.getY());
        int minSZ = SectionPos.blockToSectionCoord(min.getZ());
        int maxSX = SectionPos.blockToSectionCoord(max.getX());
        int maxSY = SectionPos.blockToSectionCoord(max.getY());
        int maxSZ = SectionPos.blockToSectionCoord(max.getZ());

        var result = new ArrayList<BlockBox>((maxSX - minSX + 1) * (maxSY - minSY + 1) * (maxSZ - minSZ + 1));
        for (int sy = minSY; sy <= maxSY; sy++) {
            int y0 = Math.max(min.getY(), SectionPos.sectionToBlockCoord(sy));
            int y1 = Math.min(max.getY(), SectionPos.sectionToBlockCoord(sy, 15));
            for (int sz = minSZ; sz <= maxSZ; sz++) {
                int z0 = Math.max(min.getZ(), SectionPos.sectionToBlockCoord(sz));
                int z1 = Math.min(max.getZ(), SectionPos.sectionToBlockCoord(sz, 15));
                for (int sx = minSX; sx <= maxSX; sx++) {
                    int x0 = Math.max(min.getX(), SectionPos.sectionToBlockCoord(sx));
                    int x1 = Math.min(max.getX(), SectionPos.sectionToBlockCoord(sx, 15));
                    result.add(box(x0, y0, z0, x1, y1, z1));
                }
            }
        }
        return result;
    }

    private static BlockBox box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new BlockBox(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }
}

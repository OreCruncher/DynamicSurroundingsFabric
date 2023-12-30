package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class Cuboid {

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public Cuboid(final BlockPos[] points) {
        this(points[0], points[1]);
    }

    public Cuboid(final BlockPos vx1, final BlockPos vx2) {
        this(Math.min(vx1.getX(), vx2.getX()),
                Math.min(vx1.getY(), vx2.getY()),
                Math.min(vx1.getZ(), vx2.getZ()),
                Math.max(vx1.getX(), vx2.getX()),
                Math.max(vx1.getY(), vx2.getY()),
                Math.max(vx1.getZ(), vx2.getZ()));
    }

    public Cuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public boolean contains(final BlockPos p) {
        return p.getX() >= this.minX && p.getX() <= this.maxX
                && p.getY() >= this.minY && p.getY() <= this.maxY
                && p.getZ() >= this.minZ && p.getZ() <= this.maxZ;
    }

    public BlockPos maximum() {
        return new BlockPos(this.maxX, this.maxY, this.maxZ);
    }

    public BlockPos minimum() {
        return new BlockPos(this.minX, this.minY, this.minZ);
    }

    public long volume() {
        var x = Math.abs(this.maxX - this.minX);
        var y = Math.abs(this.maxY - this.minY);
        var z = Math.abs(this.maxZ - this.minZ);
        return (long) x * y * z;
    }

    public boolean intersects(Cuboid o) {
        return this.minX <= o.maxX
                && this.maxX >= o.minX
                && this.minY <= o.maxY
                && this.maxY >= o.minY
                && this.minZ <= o.maxZ
                && this.maxZ >= o.minZ;
    }

    @Nullable
    public Cuboid intersection(Cuboid o) {
        if (this.intersects(o)) {
            int minX = Math.max(this.minX, o.minX);
            int minY = Math.max(this.minY, o.minY);
            int minZ = Math.max(this.minZ, o.minZ);
            int maxX = Math.min(this.maxX, o.maxX);
            int maxY = Math.min(this.maxY, o.maxY);
            int maxZ = Math.min(this.maxZ, o.maxZ);
            return new Cuboid(minX, minY, minZ, maxX, maxY, maxZ);
        }
        return null;
    }

    @Override
    public String toString() {
        return "Cuboid{min=(%d,%d,%d),max=(%d,%d,%d),volume=%d}".formatted(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ, this.volume());
    }
}
package org.orecruncher.dsurround.lib.scanner;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.BlockPosUtil;

@Environment(EnvType.CLIENT)
public class Cuboid {

    protected final BlockPos[] vertices = new BlockPos[8];
    protected final int volume;
    protected final BlockPos minPoint;
    protected final BlockPos maxPoint;

    public Cuboid( final BlockPos[] points) {
        this(points[0], points[1]);
    }

    public Cuboid( final BlockPos vx1,  final BlockPos vx2) {

        this.minPoint = BlockPosUtil.createMinPoint(vx1, vx2);
        this.maxPoint = BlockPosUtil.createMaxPoint(vx1, vx2);

        final BlockPos t = this.maxPoint.subtract(this.minPoint);
        this.volume = t.getX() * t.getY() * t.getZ();

        this.vertices[0] = this.minPoint;
        this.vertices[1] = this.maxPoint;
        this.vertices[2] = new BlockPos(this.minPoint.getX(), this.maxPoint.getY(), this.maxPoint.getZ());
        this.vertices[3] = new BlockPos(this.maxPoint.getX(), this.minPoint.getY(), this.minPoint.getZ());
        this.vertices[4] = new BlockPos(this.maxPoint.getX(), this.minPoint.getY(), this.maxPoint.getZ());
        this.vertices[5] = new BlockPos(this.minPoint.getX(), this.minPoint.getY(), this.maxPoint.getZ());
        this.vertices[6] = new BlockPos(this.minPoint.getX(), this.maxPoint.getY(), this.minPoint.getZ());
        this.vertices[7] = new BlockPos(this.maxPoint.getX(), this.maxPoint.getY(), this.minPoint.getZ());
    }

    public boolean contains( final BlockPos p) {
        return BlockPosUtil.contains(p, this.minPoint, this.maxPoint);
    }

    
    public BlockPos maximum() {
        return this.maxPoint;
    }

    
    public BlockPos minimum() {
        return this.minPoint;
    }

    public long volume() {
        return this.volume;
    }

    @Nullable
    public Cuboid intersection( final Cuboid o) {
        BlockPos vx1 = null;
        for (final BlockPos vx : this.vertices) {
            if (o.contains(vx)) {
                vx1 = vx;
                break;
            }
        }

        if (vx1 == null)
            return null;

        BlockPos vx2 = null;
        for (final BlockPos vx : o.vertices) {
            if (contains(vx) && BlockPosUtil.canFormCuboid(vx, vx1)) {
                vx2 = vx;
                break;
            }
        }

        return vx2 == null ? null : new Cuboid(vx1, vx2);
    }

}
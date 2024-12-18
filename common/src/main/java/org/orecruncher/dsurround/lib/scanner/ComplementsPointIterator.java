package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public class ComplementsPointIterator implements IPointIterator {

    protected CuboidPointIterator[] segments = new CuboidPointIterator[3];
    protected int activeSegment = 0;
    protected BlockPos peeked = null;

    public ComplementsPointIterator(final Cuboid volume, final Cuboid intersect) {
        // This function makes some important assumptions about volume and
        // intersect:
        // 1) Intersect is completely contained within volume
        // 2) Intersect always shares at least 3 faces with volume (shares at
        // least 1 "corner")
        // These conditions are met easily by always taking intersects of two
        // cuboids
        // of the same size ie:
        //
        // Cuboid oldVolume = new Cuboid(0,0,0,64,32,64);
        // Cuboid newVolume = oldVolume.translated(3,10,20);
        // Cuboid intersect = newVolume.intersect(oldVolume);
        // ComplementsPointIterator outOfRange = new
        // ComplementsPointIterator(oldVolume,intersect);
        // ComplementsPointIterator inRange = new
        // ComplementsPointIterator(newVolume,intersect);
        //

        final BlockPos vmax = volume.maximum();
        final BlockPos imax = intersect.maximum();
        final BlockPos vmin = volume.minimum();
        final BlockPos imin = intersect.minimum();

        if (vmax.getX() != imax.getX() || vmin.getX() != imin.getX()) {
            if (vmax.getX() > imax.getX())
                this.segments[0] = new CuboidPointIterator(new BlockPos(imax.getX(), vmin.getY(), vmin.getZ()),
                        new BlockPos(vmax.getX(), vmax.getY(), vmax.getZ()));
            else
                this.segments[0] = new CuboidPointIterator(new BlockPos(vmin.getX(), vmin.getY(), vmin.getZ()),
                        new BlockPos(imin.getX(), vmax.getY(), vmax.getZ()));
        } else {
            this.segments[0] = CuboidPointIterator.NULL_ITERATOR;
        }

        if (vmax.getY() != imax.getY() || vmin.getY() != imin.getY()) {
            if (vmax.getY() > imax.getY())
                this.segments[1] = new CuboidPointIterator(new BlockPos(imin.getX(), imax.getY(), vmin.getZ()),
                        new BlockPos(imax.getX(), vmax.getY(), vmax.getZ()));
            else
                this.segments[1] = new CuboidPointIterator(new BlockPos(imin.getX(), vmin.getY(), vmin.getZ()),
                        new BlockPos(imax.getX(), imin.getY(), vmax.getZ()));
        } else {
            this.segments[1] = CuboidPointIterator.NULL_ITERATOR;
        }

        if (vmax.getZ() != imax.getZ() || vmin.getZ() != imin.getZ()) {
            if (vmax.getZ() > imax.getZ())
                this.segments[2] = new CuboidPointIterator(new BlockPos(imin.getX(), imin.getY(), imax.getZ()),
                        new BlockPos(imax.getX(), imax.getY(), vmax.getZ()));
            else
                this.segments[2] = new CuboidPointIterator(new BlockPos(imin.getX(), imin.getY(), vmin.getZ()),
                        new BlockPos(imax.getX(), imax.getY(), imin.getZ()));
        } else {
            this.segments[2] = CuboidPointIterator.NULL_ITERATOR;
        }

        this.peeked = next0();
    }

    protected BlockPos next0() {
        while (this.activeSegment < this.segments.length) {
            final BlockPos rv = this.segments[this.activeSegment].next();
            if (rv != null)
                return rv;
            this.activeSegment++;
        }
        return null;
    }

    @Override
    @Nullable
    public BlockPos peek() {
        return this.peeked;
    }

    @Override
    @Nullable
    public BlockPos next() {
        final BlockPos result = this.peeked;
        this.peeked = next0();
        return result;
    }

}
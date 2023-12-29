package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

public class CuboidPointIterator implements IPointIterator {

    static final CuboidPointIterator NULL_ITERATOR = new CuboidPointIterator() {

        @Override
        public BlockPos next() {
            return null;
        }

        @Override
        public BlockPos peek() {
            return null;
        }

    };

    protected final Iterator<BlockPos> itr;
    protected BlockPos peeked;

    private CuboidPointIterator() {
        this.itr = null;
    }

    public CuboidPointIterator(final BlockPos[] points) {
        this(points[0], points[1]);
    }

    public CuboidPointIterator(final BlockPos p1, final BlockPos p2) {
        this.itr = BlockPos.iterate(p1, p2).iterator();
        if (this.itr.hasNext())
            this.peeked = this.itr.next();
    }

    @Override
    @Nullable
    public BlockPos next() {
        final BlockPos result = this.peeked;
        this.peeked = this.itr.hasNext() ? this.itr.next() : null;
        return result;
    }

    @Override
    @Nullable
    public BlockPos peek() {
        return this.peeked;
    }

}
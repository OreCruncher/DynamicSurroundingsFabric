package org.orecruncher.dsurround.lib.scanner;

import com.google.common.collect.AbstractIterator;
import net.minecraft.core.BlockPos;
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
        this.itr = BlockPos.betweenClosed(p1, p2).iterator();
        //this.itr = iterateCuboid(p1.getX(), p1.getY(), p1.getZ(), p2.getX(), p2.getY(), p2.getZ()).iterator();
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

    /**
     * Customized cube iterator that favors iterating x, z, and then y as to maximize on CPU cache hits when
     * traversing the cube.
     */
    private static Iterable<BlockPos> iterateCuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int length = maxX - minX + 1;
        int height = maxY - minY + 1;
        int width = maxZ - minZ + 1;
        int volume = length * height * width;
        return () -> new AbstractIterator<>() {
            private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            private int index;

            protected BlockPos computeNext() {
                if (this.index == volume) {
                    return this.endOfData();
                } else {
                    int dX = this.index % length;
                    int jx = this.index / length;
                    int dZ = jx % width;
                    int dY = jx / width;
                    ++this.index;
                    return this.pos.set(minX + dX, minY + dY, minZ + dZ);
                }
            }
        };
    }
}
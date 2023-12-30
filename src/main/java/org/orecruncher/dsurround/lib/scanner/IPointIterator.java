package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.util.math.BlockPos;

public interface IPointIterator {
    BlockPos next();

    BlockPos peek();
}
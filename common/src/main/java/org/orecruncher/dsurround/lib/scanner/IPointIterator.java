package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.core.BlockPos;

public interface IPointIterator {
    BlockPos next();

    BlockPos peek();
}
package org.orecruncher.dsurround.lib.scanner;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public interface IPointIterator {
    BlockPos next();

    BlockPos peek();
}
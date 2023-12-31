package org.orecruncher.dsurround.mixinutils;

import org.orecruncher.dsurround.config.block.BlockInfo;

public interface IBlockStateExtended {

    BlockInfo dsurround_getBlockInfo();

    void dsurround_setBlockInfo(final BlockInfo data);
}

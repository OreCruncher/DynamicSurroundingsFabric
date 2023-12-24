package org.orecruncher.dsurround.config.libraries;

import net.minecraft.block.BlockState;
import org.orecruncher.dsurround.config.block.BlockInfo;

import java.util.stream.Stream;

public interface IBlockLibrary extends ILibrary {
    BlockInfo getBlockInfo(BlockState state);

    Stream<String> dumpBlockStates();
    Stream<String> dumpBlockConfigRules();
    Stream<String> dumpBlocks(boolean noStates);
}

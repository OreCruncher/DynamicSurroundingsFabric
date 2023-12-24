package org.orecruncher.dsurround.config.libraries;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import org.orecruncher.dsurround.config.block.BlockInfo;

import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public interface IBlockLibrary extends ILibrary {
    BlockInfo getBlockInfo(BlockState state);

    Stream<String> dumpBlockStates();
    Stream<String> dumpBlockConfigRules();
    Stream<String> dumpBlocks(boolean noStates);
}

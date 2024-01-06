package org.orecruncher.dsurround.config.libraries;

import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.config.block.BlockInfo;

import java.util.Optional;
import java.util.stream.Stream;

public interface IBlockLibrary extends ILibrary {
    /**
     * Obtains the cached block information data instance from the given BlockState. If
     * not present, a BlockInfo instance will be created and cached prior to returning.
     * Do not call this API unless logic is on the client thread.
     */
    BlockInfo getBlockInfo(BlockState state);

    /**
     * Obtains the cached block information data instance from the given BlockState. If
     * not present, the DEFAULT BlockInfo instance is returned. This API is safe to call
     * on a non-client thread.
     */
    BlockInfo getBlockInfoWeak(BlockState state);

    Stream<String> dumpBlockStates();
    Stream<String> dumpBlockConfigRules();
    Stream<String> dumpBlocks(boolean noStates);
}

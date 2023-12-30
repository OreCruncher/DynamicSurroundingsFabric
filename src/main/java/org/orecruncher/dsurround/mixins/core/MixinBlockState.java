package org.orecruncher.dsurround.mixins.core;

import net.minecraft.block.BlockState;
import org.orecruncher.dsurround.config.block.BlockInfo;
import org.orecruncher.dsurround.mixinutils.IBlockStateExtended;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockState.class)
public class MixinBlockState implements IBlockStateExtended {

    private BlockInfo dsurround_info;

    @Override
    public BlockInfo getBlockInfo() {
        return this.dsurround_info;
    }

    @Override
    public void setBlockInfo(BlockInfo data) {
        this.dsurround_info = data;
    }
}

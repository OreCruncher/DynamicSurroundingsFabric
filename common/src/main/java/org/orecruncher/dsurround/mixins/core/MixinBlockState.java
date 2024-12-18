package org.orecruncher.dsurround.mixins.core;

import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.config.block.BlockInfo;
import org.orecruncher.dsurround.mixinutils.IBlockStateExtended;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockState.class)
public class MixinBlockState implements IBlockStateExtended {

    @Unique
    private BlockInfo dsurround_info;

    @Override
    public BlockInfo dsurround_getBlockInfo() {
        return this.dsurround_info;
    }

    @Override
    public void dsurround_setBlockInfo(BlockInfo data) {
        this.dsurround_info = data;
    }
}

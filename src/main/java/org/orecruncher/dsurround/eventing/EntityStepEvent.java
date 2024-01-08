package org.orecruncher.dsurround.eventing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

public record EntityStepEvent(Entity entity, BlockPos blockPos, BlockState blockState) {

}

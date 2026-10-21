package org.orecruncher.dsurround.processing.accents;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface IFootstepAccentProvider {

    void collect(LivingEntity entity, BlockPos pos, BlockState posState, boolean isWaterLogged, StepAccents acoustics);

    boolean isEnabled();
}

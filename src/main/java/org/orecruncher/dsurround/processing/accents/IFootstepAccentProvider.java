package org.orecruncher.dsurround.processing.accents;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.sound.ISoundFactory;

public interface IFootstepAccentProvider {
    void provide(LivingEntity entity, BlockPos pos, BlockState posState, boolean isWaterLogged, ObjectArray<ISoundFactory> acoustics);

    boolean isEnabled();
}
